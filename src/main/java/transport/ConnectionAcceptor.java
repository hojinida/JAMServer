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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import main.java.util.ConnectionManager;

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
  private final AtomicBoolean started = new AtomicBoolean(false);
  private volatile boolean shutdown = false;

  public ConnectionAcceptor(int size, InetSocketAddress listenAddress,
      EventProcessor eventProcessor) throws IOException {
    this.size = size;
    this.listenAddress = listenAddress;
    this.eventProcessor = eventProcessor;
    this.serverChannels = new ServerSocketChannel[size];
    this.selectors = new Selector[size];
    this.executor = Executors.newFixedThreadPool(size, r -> {
      Thread thread = new Thread(r, "ConnectionAcceptor-" + Thread.currentThread().getId());
      thread.setDaemon(true);
      return thread;
    });

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
    if (!started.compareAndSet(false, true)) {
      return;
    }

    for (int i = 0; i < size; i++) {
      final int index = i;
      executor.execute(() -> run(index));
    }

    System.out.println("ConnectionAcceptor started with " + size + " threads");
  }

  private void run(int index) {
    Selector selector = selectors[index];
    String threadName = "ConnectionAcceptor-" + index;

    System.out.println(threadName + " started");

    try {
      while (!Thread.currentThread().isInterrupted() && !shutdown) {
        try {
          selector.select(100);

          if (shutdown) {
            break;
          }

          Iterator<SelectionKey> it = selector.selectedKeys().iterator();
          while (it.hasNext()) {
            SelectionKey key = it.next();
            it.remove();

            if (key.isValid() && key.isAcceptable()) {
              acceptConnection(key);
            }
          }
        } catch (IOException e) {
          if (!shutdown) {
            System.err.println(threadName + " IOException during select: " + e.getMessage());
            e.printStackTrace();
          }
        } catch (Exception e) {
          System.err.println(threadName + " Unexpected exception: " + e.getMessage());
          e.printStackTrace();
        }
      }
    } catch (Throwable t) {
      System.err.println(threadName + " Fatal error, thread terminating: " + t.getMessage());
      t.printStackTrace();
    } finally {
      System.out.println(threadName + " terminated");
    }
  }

  private void acceptConnection(SelectionKey key) {
    ServerSocketChannel server = (ServerSocketChannel) key.channel();

    for (int i = 0; i < MAX_ACCEPTS_PER_LOOP; i++) {
      SocketChannel client = null;
      try {
        client = server.accept();
        if (client == null) {
          break;
        }

        if (!ConnectionManager.tryIncrement()) {
          System.err.println("Connection rejected: limit exceeded");
          client.close();
          continue;
        }

        boolean configured = false;
        try {
          client.configureBlocking(false);
          client.setOption(StandardSocketOptions.TCP_NODELAY, true);
          client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
          configured = true;
        } catch (IOException e) {
          System.err.println("Failed to configure client socket: " + e.getMessage());
        }

        if (!configured) {
          ConnectionManager.decrement();
          client.close();
          continue;
        }

        boolean registered = false;
        try {
          int processorIndex = (int) (WORKER_COUNTER.getAndIncrement() % eventProcessor.size());
          eventProcessor.registerChannel(client, processorIndex);
          registered = true;
        } catch (Exception e) {
          System.err.println("Failed to register channel with EventProcessor: " + e.getMessage());
          e.printStackTrace();
        }

        if (!registered) {
          ConnectionManager.decrement();
          client.close();
        }

      } catch (IOException e) {
        System.err.println("Error accepting connection: " + e.getMessage());
        if (client != null) {
          try {
            client.close();
          } catch (IOException ignored) {
          }
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (shutdown) {
      return;
    }

    shutdown = true;
    System.out.println("Shutting down ConnectionAcceptor...");

    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println(
            "ConnectionAcceptor executor did not terminate gracefully, forcing shutdown");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    for (int i = 0; i < selectors.length; i++) {
      if (selectors[i] != null) {
        try {
          selectors[i].wakeup();
          selectors[i].close();
        } catch (IOException e) {
          System.err.println("Error closing selector " + i + ": " + e.getMessage());
        }
      }
    }

    for (int i = 0; i < serverChannels.length; i++) {
      if (serverChannels[i] != null) {
        try {
          serverChannels[i].close();
        } catch (IOException e) {
          System.err.println("Error closing server channel " + i + ": " + e.getMessage());
        }
      }
    }

    System.out.println("ConnectionAcceptor shutdown completed");
  }
}
