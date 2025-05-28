package main.java.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import main.java.transport.ChannelInitializer;
import main.java.transport.ConnectionAcceptor;
import main.java.transport.EventProcessor;
import main.java.util.buffer.BufferPool;
import main.java.util.business.BusinessExecutor;

public class JamServer implements AutoCloseable {

  private final ConnectionAcceptor connectionAcceptor;
  private final EventProcessor eventProcessor;
  private final BusinessExecutor businessExecutor;
  private volatile boolean running;

  private static final int DEFAULT_PORT = 8888;
  private static final int ACCEPTOR_THREADS = 2;
  private static final long GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 10;


  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    BufferPool.getInstance();
    this.running = true;

    this.businessExecutor = new BusinessExecutor();
    ChannelInitializer initializer = new ChannelInitializer(businessExecutor);

    this.eventProcessor = new EventProcessor(nCores * 2, initializer);
    this.eventProcessor.start();

    this.connectionAcceptor = new ConnectionAcceptor(ACCEPTOR_THREADS, address, eventProcessor);
    this.connectionAcceptor.start();

    System.out.println("JamServer started on port " + port);
  }

  @Override
  public void close() throws IOException {
    if (!running) {
      return;
    }

    running = false;
    System.out.println("Server shutdown sequence initiated...");

    if (connectionAcceptor != null) {
      System.out.println("Closing ConnectionAcceptor...");
      connectionAcceptor.close();
    }

    if (eventProcessor != null) {
      System.out.println("Closing EventProcessor...");
      eventProcessor.close();
    }

    if (businessExecutor != null) {
      System.out.println("Closing BusinessExecutor...");
      businessExecutor.close();
    }

    System.out.println("Shutting down BufferPool...");
    BufferPool.getInstance().shutdown();

    System.out.println("Server shutdown completed.");
  }


  public void shutdownGracefully(long timeout, TimeUnit unit) {
    System.out.println("Attempting graceful shutdown (" + timeout + " " + unit.toString() + ")...");
    try {
      close();
    } catch (Exception e) {
      System.err.println("Error during graceful shutdown: " + e.getMessage());
      e.printStackTrace();
      try {
        if (running) {
          close();
        }
      } catch (IOException ioException) {
        System.err.println("Error during forced shutdown: " + ioException.getMessage());
      }
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final int port = DEFAULT_PORT;
    final JamServer server = new JamServer(port);

    CountDownLatch shutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        System.out.println("Shutdown hook triggered.");
        server.shutdownGracefully(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (Exception e) {
        System.err.println("Error during shutdown hook execution: " + e.getMessage());
        e.printStackTrace();
      } finally {
        shutdownLatch.countDown();
      }
    }, "Shutdown-Hook"));

    System.out.println("Press Ctrl+C to stop the server.");
    shutdownLatch.await();
    System.out.println("Main thread exiting.");
  }
}
