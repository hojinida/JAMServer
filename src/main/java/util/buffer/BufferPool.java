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
    System.out.println(
        "BufferPool initialized: READ_POOL_SIZE=" + READ_POOL_SIZE + ", RESPONSE_POOL_SIZE="
            + RESPONSE_POOL_SIZE);
  }

  public static BufferPool getInstance() {
    return INSTANCE;
  }

  public ByteBuffer acquireReadBuffer() {
    ByteBuffer buffer = readBuffers.poll();

    if (buffer != null) {
      buffer.clear();
      return buffer;
    }

    System.out.println("Read buffer pool empty - Allocating new buffer.");
    return ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
  }

  public ByteBuffer acquireResponseBuffer() {
    ByteBuffer buffer = responseBuffers.poll();

    if (buffer != null) {
      buffer.clear();
      return buffer;
    }

    System.out.println("Response buffer pool empty - Allocating new buffer.");
    return ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE);
  }

  public void releaseReadBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != READ_BUFFER_SIZE) {
      System.err.println("Invalid read buffer returned to pool. Discarding.");
      return;
    }

    buffer.clear();
    readBuffers.offer(buffer);
  }

  public void releaseResponseBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.isDirect() || buffer.capacity() != RESPONSE_BUFFER_SIZE) {
      System.err.println("Invalid response buffer returned to pool. Discarding.");
      return;
    }

    buffer.clear();
    responseBuffers.offer(buffer);
  }

  public void shutdown() {
    System.out.println("Shutting down BufferPool...");
    readBuffers.clear();
    responseBuffers.clear();
    System.out.println("BufferPool shutdown completed.");
  }
}
