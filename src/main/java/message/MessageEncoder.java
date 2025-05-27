package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {
  private static final int HEADER_SIZE = 6;
  private static final MessageEncoder INSTANCE = new MessageEncoder();

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  public ByteBuffer encode(Message message) throws InterruptedException {
    return encodeWithResponseBuffer(message);
  }

  public ByteBuffer encodeWithResponseBuffer(Message message) throws InterruptedException {
    ByteBuffer payload = message.getPayload();
    int totalSize = HEADER_SIZE + payload.remaining();

    if (totalSize > 64) {
      throw new IllegalArgumentException("Response too large for response buffer: " + totalSize + " bytes");
    }

    ByteBuffer buffer = BufferPool.getInstance().acquireResponseBuffer();

    buffer.clear();
    buffer.putInt(payload.remaining());
    buffer.putShort(message.getTypeValue());
    buffer.put(payload);
    buffer.flip();

    return buffer;
  }
}
