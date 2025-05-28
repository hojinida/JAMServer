package main.java.util.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BufferPool {

  private static final int READ_BUFFER_SIZE = 1024;
  private static final int RESPONSE_BUFFER_SIZE = 64;

  private final int readPoolSize;
  private final int responsePoolSize;

  private static final BufferPool INSTANCE = new BufferPool(30000, 50000);

  private final ConcurrentLinkedQueue<ByteBuffer> readBuffers;
  private final ConcurrentLinkedQueue<ByteBuffer> responseBuffers;

  private final AtomicInteger readBuffersCreated = new AtomicInteger(0);
  private final AtomicInteger responseBuffersCreated = new AtomicInteger(0);

  private BufferPool(int readPoolSize, int responsePoolSize) {
    this.readPoolSize = readPoolSize;
    this.responsePoolSize = responsePoolSize;

    this.readBuffers = new ConcurrentLinkedQueue<>();
    this.responseBuffers = new ConcurrentLinkedQueue<>();

    for (int i = 0; i < readPoolSize; i++) {
      readBuffers.offer(ByteBuffer.allocateDirect(READ_BUFFER_SIZE));
    }

    for (int i = 0; i < responsePoolSize; i++) {
      responseBuffers.offer(ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE));
    }

    System.out.println(
        "BufferPool initialized: " + "READ_POOL_SIZE=" + readPoolSize + ", RESPONSE_POOL_SIZE="
            + responsePoolSize);
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

    int created = readBuffersCreated.get();
    if (created < readPoolSize / 2) {
      readBuffersCreated.incrementAndGet();
      System.err.println(
          "Read buffer pool depleted - Creating new buffer (" + (created + 1) + "/" + (readPoolSize
              / 2) + " extra buffers)");
      return ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
    }

    throw new IllegalStateException("Read buffer pool exhausted and cannot create more buffers");
  }

  public ByteBuffer acquireResponseBuffer() {
    ByteBuffer buffer = responseBuffers.poll();

    if (buffer != null) {
      buffer.clear();
      return buffer;
    }

    int created = responseBuffersCreated.get();
    if (created < responsePoolSize / 2) {
      responseBuffersCreated.incrementAndGet();
      System.err.println(
          "Response buffer pool depleted - Creating new buffer (" + (created + 1) + "/" + (
              responsePoolSize / 2) + " extra buffers)");
      return ByteBuffer.allocateDirect(RESPONSE_BUFFER_SIZE);
    }

    throw new IllegalStateException(
        "Response buffer pool exhausted and cannot create more buffers");
  }

  public void releaseReadBuffer(ByteBuffer buffer) {
    if (buffer == null) {
      throw new IllegalArgumentException("Cannot release null buffer");
    }

    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("Buffer must be direct: " + buffer);
    }

    if (buffer.capacity() != READ_BUFFER_SIZE) {
      throw new IllegalArgumentException(
          "Invalid read buffer capacity: expected " + READ_BUFFER_SIZE + ", got "
              + buffer.capacity());
    }

    buffer.clear();
    readBuffers.offer(buffer);
  }

  public void releaseResponseBuffer(ByteBuffer buffer) {
    if (buffer == null) {
      throw new IllegalArgumentException("Cannot release null buffer");
    }

    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("Buffer must be direct: " + buffer);
    }

    if (buffer.capacity() != RESPONSE_BUFFER_SIZE) {
      throw new IllegalArgumentException(
          "Invalid response buffer capacity: expected " + RESPONSE_BUFFER_SIZE + ", got "
              + buffer.capacity());
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
