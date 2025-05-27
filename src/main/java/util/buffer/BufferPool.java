package main.java.util.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BufferPool {
  private static final int READ_BUFFER_SIZE = 1024;
  private static final int READ_POOL_SIZE = 30000;

  private static final int RESPONSE_BUFFER_SIZE = 64;
  private static final int RESPONSE_POOL_SIZE = 50000;

  private static final BufferPool INSTANCE = new BufferPool();

  private final ConcurrentLinkedQueue<ByteBuffer> readBuffers;
  private final ConcurrentLinkedQueue<ByteBuffer> responseBuffers;

  private BufferPool() {
    readBuffers = new ConcurrentLinkedQueue<>();
    responseBuffers = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < READ_POOL_SIZE; i++) {
      readBuffers.offer(ByteBuffer.allocateDirect(READ_BUFFER_SIZE));
    }

    for (int i = 0; i < RESPONSE_POOL_SIZE; i++) {
      responseBuffers.offer(ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE));
    }
  }

  public static BufferPool getInstance() {
    return INSTANCE;
  }

  public ByteBuffer acquireReadBuffer() throws InterruptedException {
    ByteBuffer buffer = readBuffers.poll();

    if (buffer != null) {
      buffer.clear();
      return buffer;
    }

    System.out.println("Read 버퍼 풀 부족 - 새 버퍼 할당");
    return ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
  }

  public ByteBuffer acquireResponseBuffer() throws InterruptedException {
    ByteBuffer buffer = responseBuffers.poll();

    if (buffer != null) {
      buffer.clear();
      return buffer;
    }

    System.out.println("Response 버퍼 풀 부족 - 새 버퍼 할당");
    return ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE);
  }

  public void releaseReadBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != READ_BUFFER_SIZE) {
      return;
    }

    buffer.clear();
  }

  public void releaseResponseBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != RESPONSE_BUFFER_SIZE) {
      return;
    }

    buffer.clear();
  }

  public void shutdown() {
    readBuffers.clear();
    responseBuffers.clear();
  }
}
