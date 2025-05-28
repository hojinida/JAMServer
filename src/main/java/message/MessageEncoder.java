package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {

  private static final int HEADER_SIZE = 6;
  private static final MessageEncoder INSTANCE = new MessageEncoder();

  private MessageEncoder() {
  }

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  public ByteBuffer encode(Message message) {
    ByteBuffer payload = message.getPayload();
    int payloadSize = payload.remaining();
    int totalSize = HEADER_SIZE + payloadSize;

    ByteBuffer buffer;
    buffer = BufferPool.getInstance().acquireResponseBuffer();

    if (totalSize > buffer.capacity()) {
      BufferPool.getInstance().releaseResponseBuffer(buffer);
      throw new IllegalArgumentException(
          "Encoded message size (" + totalSize + ") exceeds response buffer capacity ("
              + buffer.capacity() + ").");
    }

    buffer.clear();
    buffer.putInt(payloadSize);
    buffer.putShort(message.getTypeValue());
    buffer.put(payload);
    buffer.flip();

    return buffer;
  }
}
