package main.java.transport;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import main.java.core.ConnectionManager;

public class ConnectionAcceptor implements Closeable {
  private static final int MAX_ACCEPTS_PER_LOOP = 64;
  private static final int BACKLOG = 1024;
  private static final int RECEIVE_BUFFER_SIZE = 65536;
  private static final AtomicLong WORKER_COUNTER = new AtomicLong(0);

  private final InetSocketAddress listenAddress;
  private final EventProcessor eventProcessor;
  private final ServerSocketChannel[] serverChannels;
  private final Selector[] selectors;
  private final ExecutorService executor;
  private final int size;
  private volatile boolean started = false;

  public ConnectionAcceptor(int size, InetSocketAddress listenAddress, EventProcessor eventProcessor) throws IOException {
    this.size = size;
    this.listenAddress = listenAddress;
    this.eventProcessor = eventProcessor;
    this.serverChannels = new ServerSocketChannel[size];
    this.selectors = new Selector[size];
    this.executor = Executors.newFixedThreadPool(size);

    for (int i = 0; i < size; i++) {
      this.selectors[i] = Selector.open();
    }

    initializeServerChannels();
  }

  private void initializeServerChannels() throws IOException {
    for (int i = 0; i < size; i++) {
      ServerSocketChannel serverChannel = ServerSocketChannel.open();
      serverChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
      serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, RECEIVE_BUFFER_SIZE);
      serverChannel.configureBlocking(false);
      serverChannel.socket().bind(listenAddress, BACKLOG);
      serverChannel.register(selectors[i], SelectionKey.OP_ACCEPT);
      serverChannels[i] = serverChannel;
    }
  }

  public void start() {
    if (started) {
      return;
    }

    for (int i = 0; i < size; i++) {
      final int index = i;
      executor.execute(() -> run(index));
    }

    started = true;
  }

  private void run(int index) {
    Selector selector = selectors[index];
    try {
      while (!Thread.currentThread().isInterrupted()) {
        selector.select();
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          if (key.isValid() && key.isAcceptable()) {
            acceptConnection(key);
          }
        }
      }
    } catch (Throwable t) {
      // 무시
    }
  }

  private void acceptConnection(SelectionKey key) throws IOException {
    ServerSocketChannel server = (ServerSocketChannel) key.channel();
    for (int i = 0; i < MAX_ACCEPTS_PER_LOOP; i++) {
      SocketChannel client = server.accept();
      if (client == null) {
        break;
      }

      boolean acquired = ConnectionManager.tryIncrement();
      if (!acquired) {
        client.close();
        continue;
      }

      boolean registered = false;
      try {
        client.configureBlocking(false);
        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

        int processorIndex = (int) (WORKER_COUNTER.getAndIncrement() % eventProcessor.size());
        eventProcessor.registerChannel(client, processorIndex);
        registered = true;
      } finally {
        if (!registered) {
          ConnectionManager.decrement();
          try {
            client.close();
          } catch (IOException ignored) {}
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    executor.shutdown();

    for (Selector selector : selectors) {
      try {
        selector.wakeup();
        selector.close();
      } catch (IOException e) {
        // 무시
      }
    }

    for (ServerSocketChannel channel : serverChannels) {
      try {
        channel.close();
      } catch (IOException e) {
        // 무시
      }
    }
  }
}
