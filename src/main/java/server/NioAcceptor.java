package main.java.server;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import main.java.util.NioThreadFactory;

public class NioAcceptor implements Closeable {

  private static final int MAX_ACCEPTS_PER_LOOP = 64;
  private static final int BACKLOG = 1024;
  private static final int RECEIVE_BUFFER_SIZE = 65536;
  private static final int MAX_CONNECTIONS = 30000;

  private final NioEventLoop[] eventLoops;
  private final ServerSocketChannel serverChannel;
  private final Selector selector;
  private final ExecutorService executor;
  private final AtomicLong connectionCounter;
  private final AtomicLong workerCounter = new AtomicLong(0);
  private volatile boolean shutdown = false;

  public NioAcceptor(InetSocketAddress listenAddress, NioEventLoop[] eventLoops, AtomicLong connectionCounter) throws IOException {
    this.eventLoops = eventLoops;
    this.connectionCounter = connectionCounter;

    this.selector = Selector.open();
    this.executor = Executors.newSingleThreadExecutor(new NioThreadFactory("acceptor-pool"));

    this.serverChannel = ServerSocketChannel.open();
    serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
    serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, RECEIVE_BUFFER_SIZE);
    serverChannel.configureBlocking(false);
    serverChannel.socket().bind(listenAddress, BACKLOG);
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

    System.out.println("NioAcceptor listening on " + listenAddress);
  }

  public void start() {
    executor.execute(this::run);
  }

  private void run() {
    System.out.println("NioAcceptor run loop started.");
    while (!Thread.currentThread().isInterrupted() && !shutdown) {
      try {
        selector.select(1000);

        if (shutdown) break;

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          if (key.isValid() && key.isAcceptable()) {
            acceptConnection(key);
          }
        }
      } catch (IOException e) {
        if (!shutdown) e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.out.println("NioAcceptor run loop terminated.");
  }

  private void acceptConnection(SelectionKey key) {
    ServerSocketChannel server = (ServerSocketChannel) key.channel();
    for (int i = 0; i < MAX_ACCEPTS_PER_LOOP; i++) {
      SocketChannel client = null;
      try {
        client = server.accept();
        if (client == null) break;

        if (connectionCounter.get() >= MAX_CONNECTIONS) {
          System.err.println("Connection rejected: Max connections reached.");
          client.close();
          continue;
        }
        connectionCounter.incrementAndGet();

        client.configureBlocking(false);
        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

        int processorIndex = (int) (workerCounter.getAndIncrement() % eventLoops.length);
        eventLoops[processorIndex].registerChannel(client);

      } catch (IOException e) {
        System.err.println("Error accepting connection: " + e.getMessage());
        if (client != null) {
          try { client.close(); } catch (IOException ignored) {}
          connectionCounter.decrementAndGet();
        }
      } catch (Exception e) {
        System.err.println("Error configuring or registering connection: " + e.getMessage());
        if (client != null) {
          try { client.close(); } catch (IOException ignored) {}
          connectionCounter.decrementAndGet();
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (shutdown) return;
    shutdown = true;
    System.out.println("Shutting down NioAcceptor...");

    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    if (selector != null && selector.isOpen()) {
      try { selector.wakeup(); selector.close(); } catch (IOException e) { e.printStackTrace(); }
    }
    if (serverChannel != null && serverChannel.isOpen()) {
      try { serverChannel.close(); } catch (IOException e) { e.printStackTrace(); }
    }
    System.out.println("NioAcceptor shutdown completed.");
  }
}
