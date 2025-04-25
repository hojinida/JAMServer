package main.java.util.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import main.java.util.BufferTimeoutException;

public class BufferPool {
  private static final int BUFFER_SIZE = 36;

  private static final int POOL_SIZE = 30000;

  private static final long TIMEOUT_MS = 100;

  private static final BufferPool INSTANCE = new BufferPool();

  private final BlockingQueue<ByteBuffer> buffers;


  private BufferPool() {
    buffers = new ArrayBlockingQueue<>(POOL_SIZE);

    for (int i = 0; i < POOL_SIZE; i++) {
      buffers.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
    }

    System.out.println("버퍼 풀 초기화 완료: " + POOL_SIZE + "개 생성됨 (크기: " + BUFFER_SIZE + "바이트)");
  }

  public static BufferPool getInstance() {
    return INSTANCE;
  }

  public ByteBuffer acquire() throws InterruptedException {
    ByteBuffer buffer = buffers.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);

    if (buffer == null) {
      throw new BufferTimeoutException("버퍼 풀에서 버퍼를 얻는 데 실패했습니다 (Timeout: " + TIMEOUT_MS + "ms)");
    }

    buffer.clear();
    return buffer;
  }

  public void release(ByteBuffer buffer) throws InterruptedException {
    if (buffer == null) {
      return;
    }

    if (buffer.isDirect() && buffer.capacity() == BUFFER_SIZE) {
      buffer.clear();
      buffers.offer(buffer);
    }
  }

  public void shutdown() {
    buffers.clear();
    System.out.println("버퍼 풀 정리 완료");
  }
}
