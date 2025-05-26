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
  private volatile boolean running = false;


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

    System.out.println("서버 가동 port " + port);
    System.out.println("Processors: " + nCores + ", Event threads: " + (nCores * 2));
  }

  @Override
  public void close() throws IOException {
    if (!running) {
      return;
    }

    running = false;
    System.out.println("서버 종료 시작...");

    // 1. 새로운 연결 수락 중단
    if (connectionAcceptor != null) {
      System.out.println("연결 수락기 종료 중...");
      connectionAcceptor.close();
    }

    // 2. 이벤트 처리기 종료 (기존 연결들 정리)
    if (eventProcessor != null) {
      System.out.println("이벤트 처리기 종료 중...");
      eventProcessor.close();
    }

    // 3. 비즈니스 실행기 종료 (진행 중인 작업 완료 대기)
    if (businessExecutor != null) {
      System.out.println("비즈니스 실행기 종료 중...");
      businessExecutor.close();
    }

    // 4. 버퍼 풀 정리
    System.out.println("버퍼 풀 정리 중...");
    BufferPool.getInstance().shutdown();

    System.out.println("서버 종료 완료");
  }

  /**
   * Graceful shutdown with timeout
   */
  public void shutdownGracefully(long timeout, TimeUnit unit) {
    System.out.println("Graceful shutdown 시작 (타임아웃: " + timeout + " " + unit + ")");

    long startTime = System.currentTimeMillis();
    long timeoutMillis = unit.toMillis(timeout);

    try {
      // 새 연결 차단
      if (connectionAcceptor != null) {
        connectionAcceptor.close();
      }

      // 기존 연결들이 처리 완료될 때까지 대기
      while (running && (System.currentTimeMillis() - startTime) < timeoutMillis) {
        Thread.sleep(100);
        // TODO: 활성 연결 수 체크 로직 추가
      }

      close();

    } catch (Exception e) {
      System.err.println("Graceful shutdown 중 오류: " + e.getMessage());
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

    // JVM 종료 훅 등록
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("\n=== 시스템 종료 신호 감지 ===");

      try {
        // Graceful shutdown (최대 30초 대기)
        server.shutdownGracefully(30, TimeUnit.SECONDS);
      } catch (Exception e) {
        System.err.println("종료 처리 중 오류: " + e.getMessage());
        e.printStackTrace();
      } finally {
        shutdownLatch.countDown();
      }
    }, "Shutdown-Hook"));

    // 추가 모니터링 스레드
    Thread monitoringThread = new Thread(() -> {
      while (server.running) {
        try {
          Thread.sleep(30000); // 30초마다

          // 시스템 상태 출력
          Runtime runtime = Runtime.getRuntime();
          long totalMemory = runtime.totalMemory();
          long freeMemory = runtime.freeMemory();
          long usedMemory = totalMemory - freeMemory;

          System.out.printf("시스템 상태 - 사용 메모리: %dMB, 여유 메모리: %dMB%n",
              usedMemory / 1024 / 1024, freeMemory / 1024 / 1024);

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "System-Monitor");

    monitoringThread.setDaemon(true);
    monitoringThread.start();

    System.out.println("서버가 실행 중입니다. 종료하려면 Ctrl+C를 누르세요.");
    shutdownLatch.await();

    System.out.println("서버가 안전하게 종료되었습니다.");
  }
}
