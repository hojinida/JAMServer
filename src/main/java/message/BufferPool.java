package main.java.message;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BufferPool {
  // 고정된 단일 버퍼 크기 (메시지 크기에 최적화)
  private static final int BUFFER_SIZE = 36; // 메시지 크기(22) + 여유 공간

  // 충분한 버퍼 풀 크기 (대량 연결 지원)
  private static final int POOL_SIZE = 15000;

  // 싱글톤 인스턴스
  private static final BufferPool INSTANCE = new BufferPool();

  // 버퍼 풀
  private final BlockingQueue<ByteBuffer> buffers;


  /**
   * 싱글톤 생성자
   */
  private BufferPool() {
    buffers = new ArrayBlockingQueue<>(POOL_SIZE);

    // 초기 버퍼 생성
    for (int i = 0; i < POOL_SIZE; i++) {
      buffers.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
    }

    System.out.println("버퍼 풀 초기화 완료: " + POOL_SIZE + "개 생성됨 (크기: " + BUFFER_SIZE + "바이트)");
  }

  /**
   * 싱글톤 인스턴스 반환
   */
  public static BufferPool getInstance() {
    return INSTANCE;
  }

  /**
   * 특정 크기에 맞는 버퍼 풀 반환
   * (이전 코드와의 호환성 유지)
   */
  public static BufferPool getPool() {
    return INSTANCE;
  }

  /**
   * 버퍼 획득
   */
  public ByteBuffer acquire() {
    ByteBuffer buffer = buffers.poll();
    if (buffer == null) {
      // 풀이 비었으면 새로 생성
      buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    } else {
      // 버퍼 초기화
      buffer.clear();
    }

    return buffer;
  }

  /**
   * 버퍼 반환
   */
  public void release(ByteBuffer buffer) {
    if (buffer == null) {
      return;
    }

    // 적절한 크기의 Direct 버퍼만 풀에 반환
    if (buffer.isDirect() && buffer.capacity() == BUFFER_SIZE) {
      buffer.clear();
      // 풀이 가득 차지 않았으면 반환
      if (!buffers.offer(buffer)) {
        // 풀이 가득 찬 경우 무시 (GC에게 맡김)
      }
    }
    // 다른 종류의 버퍼는 GC에게 맡김
  }

  public void shutdown() {
    buffers.clear();
    System.out.println("버퍼 풀 정리 완료");
  }
}
