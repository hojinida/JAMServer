package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {
  private static final int HEADER_SIZE = 6;

  private MessageEncoder() {
  }

  public static ByteBuffer encode(Message message) throws InterruptedException {
    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }

    ByteBuffer payload = message.getPayload();
    int length = payload.remaining();

    ByteBuffer buffer = BufferPool.getInstance().acquire();

    try {
      if (buffer.capacity() < length + HEADER_SIZE) {
        throw new IllegalStateException("Buffer capacity insufficient: need " +
            (length + HEADER_SIZE) + ", got " + buffer.capacity());
      }

      buffer.putInt(length);
      buffer.putShort(message.getTypeValue());

      buffer.put(payload);

      buffer.flip();

      return buffer;
    } catch (Exception e) {
      BufferPool.getInstance().release(buffer);
      throw e;
    }
  }

  public static int encodedSize(Message message) {
    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }

    return HEADER_SIZE + message.getLength();
  }
}
