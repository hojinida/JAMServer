package main.java.util.buffer;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BufferPool {

  private static final int READ_BUFFER_SIZE = 1024;
  private static final int RESPONSE_BUFFER_SIZE = 64;


  private static final BufferPool INSTANCE = new BufferPool(30000, 50000);

  private final BlockingQueue<ByteBuffer> readBuffers;
  private final BlockingQueue<ByteBuffer> responseBuffers;

  private BufferPool(int readPoolSize, int responsePoolSize) {

    this.readBuffers = new LinkedBlockingQueue<>();
    this.responseBuffers = new LinkedBlockingQueue<>();

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
    try {
      ByteBuffer buffer = readBuffers.take();
      buffer.clear();
      return buffer;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted while waiting for read buffer.");
      throw new RuntimeException("Acquire read buffer interrupted", e);
    }
  }

  public ByteBuffer acquireResponseBuffer() {
    try {
      ByteBuffer buffer = responseBuffers.take();
      buffer.clear();
      return buffer;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted while waiting for response buffer.");
      throw new RuntimeException("Acquire response buffer interrupted", e);
    }
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
