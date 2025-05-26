package main.java.util.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public class BufferPool {
  // Read 버퍼 (채널별 하나씩, 여러 요청 처리용)
  private static final int READ_BUFFER_SIZE = 1024;
  private static final int READ_POOL_SIZE = 30000; // 최대 1000개 연결

  // Response 버퍼 (응답 전용, 정확한 크기)
  private static final int RESPONSE_BUFFER_SIZE = 64; // 54바이트 + 여유분
  private static final int RESPONSE_POOL_SIZE = 50000; // 높은 처리량 대응

  private static final BufferPool INSTANCE = new BufferPool();

  // 분리된 버퍼 풀들
  private final ConcurrentLinkedQueue<ByteBuffer> readBuffers;
  private final ConcurrentLinkedQueue<ByteBuffer> responseBuffers;

  // 통계
  private final AtomicLong readAcquired = new AtomicLong(0);
  private final AtomicLong readReleased = new AtomicLong(0);
  private final AtomicLong responseAcquired = new AtomicLong(0);
  private final AtomicLong responseReleased = new AtomicLong(0);

  private BufferPool() {
    readBuffers = new ConcurrentLinkedQueue<>();
    responseBuffers = new ConcurrentLinkedQueue<>();

    // Read 버퍼 풀 초기화 (1024 bytes)
    for (int i = 0; i < READ_POOL_SIZE; i++) {
      readBuffers.offer(ByteBuffer.allocateDirect(READ_BUFFER_SIZE));
    }

    // Response 버퍼 풀 초기화 (64 bytes)
    for (int i = 0; i < RESPONSE_POOL_SIZE; i++) {
      responseBuffers.offer(ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE));
    }

    System.out.println("이중 버퍼 시스템 초기화:");
    System.out.println("- Read 버퍼: " + READ_POOL_SIZE + "개 x " + READ_BUFFER_SIZE + " bytes");
    System.out.println("- Response 버퍼: " + RESPONSE_POOL_SIZE + "개 x " + RESPONSE_BUFFER_SIZE + " bytes");
    System.out.println("- 총 메모리: " + ((READ_POOL_SIZE * READ_BUFFER_SIZE + RESPONSE_POOL_SIZE * RESPONSE_BUFFER_SIZE) / 1024) + "KB");

    startStatsThread();
  }

  public static BufferPool getInstance() {
    return INSTANCE;
  }

  /**
   * Read 버퍼 획득 (채널 생성 시)
   */
  public ByteBuffer acquireReadBuffer() throws InterruptedException {
    ByteBuffer buffer = readBuffers.poll();

    if (buffer != null) {
      readAcquired.incrementAndGet();
      buffer.clear();
      return buffer;
    }

    // 풀 부족 시 새로 할당
    System.out.println("Read 버퍼 풀 부족 - 새 버퍼 할당");
    readAcquired.incrementAndGet();
    return ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
  }

  /**
   * Response 버퍼 획득 (응답 생성 시)
   */
  public ByteBuffer acquireResponseBuffer() throws InterruptedException {
    ByteBuffer buffer = responseBuffers.poll();

    if (buffer != null) {
      responseAcquired.incrementAndGet();
      buffer.clear();
      return buffer;
    }

    // 풀 부족 시 새로 할당
    System.out.println("Response 버퍼 풀 부족 - 새 버퍼 할당");
    responseAcquired.incrementAndGet();
    return ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE);
  }

  /**
   * Read 버퍼 반환
   */
  public void releaseReadBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != READ_BUFFER_SIZE) {
      return;
    }

    buffer.clear();
    if (readBuffers.size() < READ_POOL_SIZE && readBuffers.offer(buffer)) {
      readReleased.incrementAndGet();
    }
  }

  /**
   * Response 버퍼 반환
   */
  public void releaseResponseBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != RESPONSE_BUFFER_SIZE) {
      return;
    }

    buffer.clear();
    if (responseBuffers.size() < RESPONSE_POOL_SIZE && responseBuffers.offer(buffer)) {
      responseReleased.incrementAndGet();
    }
  }

  private void startStatsThread() {
    Thread statsThread = new Thread(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Thread.sleep(10000);

          System.out.printf("버퍼통계 - Read풀:%d/%d(사용중:%d), Response풀:%d/%d(사용중:%d)%n",
              readBuffers.size(), READ_POOL_SIZE, readAcquired.get() - readReleased.get(),
              responseBuffers.size(), RESPONSE_POOL_SIZE, responseAcquired.get() - responseReleased.get());

        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }, "DualBufferPool-Stats");

    statsThread.setDaemon(true);
    statsThread.start();
  }

  public void shutdown() {
    System.out.println("=== 이중 버퍼 풀 통계 ===");
    System.out.println("Read - 획득:" + readAcquired.get() + ", 반환:" + readReleased.get() +
        ", 사용중:" + (readAcquired.get() - readReleased.get()));
    System.out.println("Response - 획득:" + responseAcquired.get() + ", 반환:" + responseReleased.get() +
        ", 사용중:" + (responseAcquired.get() - responseReleased.get()));

    readBuffers.clear();
    responseBuffers.clear();
    System.out.println("이중 버퍼 풀 정리 완료");
  }
}
