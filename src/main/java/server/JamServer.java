package main.java.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import main.java.channel.ChannelHandler; // ChannelHandler 사용
import main.java.handler.BusinessExecutor;
import main.java.handler.HashRequestHandler;
import main.java.message.MessageDecoder;

public class JamServer implements AutoCloseable {

  private final NioAcceptor connectionAcceptor;
  private final NioEventLoop[] eventLoops;
  private final BusinessExecutor businessExecutor;
  private volatile boolean running;

  private static final int DEFAULT_PORT = 8888;
  private static final long GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 10;
  // ServerConfig.java는 나중에 추가하기로 했으므로 아직 없음

  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);
    this.running = true;

    MessageDecoder decoder = MessageDecoder.getInstance();
    this.businessExecutor = new BusinessExecutor();
    HashRequestHandler businessHandler = new HashRequestHandler(businessExecutor);
    // ChannelHandler 생성 (디코더와 비즈니스 핸들러 주입)
    ChannelHandler channelHandler = new ChannelHandler(decoder, businessHandler);
    AtomicLong connectionCounter = new AtomicLong(0);

    int eventLoopSize = nCores;
    this.eventLoops = new NioEventLoop[eventLoopSize];
    for (int i = 0; i < eventLoopSize; i++) {
      this.eventLoops[i] = new NioEventLoop(i, channelHandler, connectionCounter);
      this.eventLoops[i].start();
    }

    this.connectionAcceptor = new NioAcceptor(address, eventLoops, connectionCounter);
    this.connectionAcceptor.start();

    System.out.println("JamServer started on port " + port + " with " + eventLoopSize + " event loops.");
  }

  @Override
  public void close() throws IOException {
    if (!running) return;
    running = false;
    System.out.println("Server shutdown sequence initiated...");

    if (connectionAcceptor != null) {
      System.out.println("Closing NioAcceptor...");
      connectionAcceptor.close();
    }

    if (eventLoops != null) {
      System.out.println("Closing NioEventLoops...");
      for (NioEventLoop loop : eventLoops) {
        if (loop != null) loop.close();
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
    final int port = DEFAULT_PORT;
    final JamServer server = new JamServer(port);
    final CountDownLatch shutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        System.out.println("Shutdown hook triggered.");
        server.shutdownGracefully(GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
