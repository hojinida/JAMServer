package main.java.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import main.java.channel.ChannelHandler;
import main.java.handler.BusinessExecutor;
import main.java.handler.HashRequestHandler;
import main.java.message.MessageDecoder;

public class JamServer implements AutoCloseable {

  private final NioAcceptor[] connectionAcceptors;
  private final NioEventLoop[] eventLoops;
  private final BusinessExecutor businessExecutor;
  private volatile boolean running;

  public JamServer(int port) throws IOException {
    InetSocketAddress address = new InetSocketAddress(port);
    this.running = true;

    MessageDecoder decoder = MessageDecoder.getInstance();
    this.businessExecutor = new BusinessExecutor();
    HashRequestHandler businessHandler = new HashRequestHandler(businessExecutor);
    ChannelHandler channelHandler = new ChannelHandler(decoder, businessHandler);
    AtomicLong connectionCounter = new AtomicLong(0);

    int eventLoopSize = ServerConfig.EVENT_LOOP_COUNT;
    this.eventLoops = new NioEventLoop[eventLoopSize];
    for (int i = 0; i < eventLoopSize; i++) {
      this.eventLoops[i] = new NioEventLoop(i, channelHandler, connectionCounter);
      this.eventLoops[i].start();
    }

    int acceptorCount = ServerConfig.ACCEPTOR_COUNT;
    this.connectionAcceptors = new NioAcceptor[acceptorCount];
    for (int i = 0; i < acceptorCount; i++) {
      this.connectionAcceptors[i] = new NioAcceptor(address, eventLoops, connectionCounter, i + 1);
      this.connectionAcceptors[i].start();
    }

    System.out.println(
        "JamServer started on port " + port + " with " + acceptorCount + " acceptors and "
            + eventLoopSize + " event loops.");
  }

  @Override
  public void close() throws IOException {
    if (!running) {
      return;
    }
    running = false;
    System.out.println("Server shutdown sequence initiated...");

    if (connectionAcceptors != null) {
      System.out.println("Closing NioAcceptors...");
      for (NioAcceptor acceptor : connectionAcceptors) {
        if (acceptor != null) {
          acceptor.close();
        }
      }
    }

    if (eventLoops != null) {
      System.out.println("Closing NioEventLoops...");
      for (NioEventLoop loop : eventLoops) {
        if (loop != null) {
          loop.close();
        }
      }
    }

    if (businessExecutor != null) {
      System.out.println("Closing BusinessExecutor...");
      businessExecutor.close();
    }

    System.out.println("Server shutdown completed.");
  }

  public void shutdownGracefully(long timeout, TimeUnit unit) {
    System.out.println("Attempting graceful shutdown...");
    try {
      close();
    } catch (Exception e) {
      System.err.println("Error during graceful shutdown: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final int port = ServerConfig.DEFAULT_PORT;
    JamServer server = null;
    try {
      server = new JamServer(port);
    } catch (IOException e) {
      System.err.println("Failed to start server: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }

    final JamServer finalServer = server;
    final CountDownLatch shutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        System.out.println("Shutdown hook triggered.");
        if (finalServer != null) {
          finalServer.shutdownGracefully(ServerConfig.GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS,
              TimeUnit.SECONDS);
        }
      } catch (Exception e) {
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
