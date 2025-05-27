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


  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    BufferPool.getInstance();
    this.running = true;

    this.businessExecutor = new BusinessExecutor();
    ChannelInitializer initializer = new ChannelInitializer(businessExecutor);

    this.eventProcessor = new EventProcessor(nCores * 2, initializer);
    this.eventProcessor.start();

    this.connectionAcceptor = new ConnectionAcceptor(2, address, eventProcessor);
    this.connectionAcceptor.start();
  }

  @Override
  public void close() throws IOException {
    if (!running) {
      return;
    }

    running = false;
    System.out.println("서버 종료 시작...");

    if (connectionAcceptor != null) {
      System.out.println("연결 수락기 종료 중...");
      connectionAcceptor.close();
    }

    if (eventProcessor != null) {
      System.out.println("이벤트 처리기 종료 중...");
      eventProcessor.close();
    }

    if (businessExecutor != null) {
      System.out.println("비즈니스 실행기 종료 중...");
      businessExecutor.close();
    }

    System.out.println("버퍼 풀 정리 중...");
    BufferPool.getInstance().shutdown();

    System.out.println("서버 종료 완료");
  }

  public void shutdownGracefully(long timeout, TimeUnit unit) {
    long startTime = System.currentTimeMillis();
    long timeoutMillis = unit.toMillis(timeout);

    try {
      if (connectionAcceptor != null) {
        connectionAcceptor.close();
      }

      while (running && (System.currentTimeMillis() - startTime) < timeoutMillis) {
        Thread.sleep(100);
      }

      close();

    } catch (Exception e) {
      System.err.println("shutdown 중 오류: " + e.getMessage());
      try {
        close();
      } catch (IOException ioException) {
        System.err.println("강제 종료 중 오류: " + ioException.getMessage());
      }
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final int port = 8888;
    final JamServer server = new JamServer(port);

    CountDownLatch shutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.shutdownGracefully(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        System.err.println("종료 처리 중 오류: " + e.getMessage());
        e.printStackTrace();
      } finally {
        shutdownLatch.countDown();
      }
    }, "Shutdown-Hook"));

    shutdownLatch.await();
  }
}
