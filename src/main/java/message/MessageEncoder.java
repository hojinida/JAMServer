package main.java.message;

import java.nio.ByteBuffer;

public class MessageEncoder {

  private static final int HEADER_SIZE = 6;
  private static final MessageEncoder INSTANCE = new MessageEncoder();
  private static final int RESPONSE_BUFFER_CAPACITY = 64;

  private MessageEncoder() {
  }

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  public ByteBuffer encode(Message message) {
    ByteBuffer payload = message.getPayload();
    int payloadSize = payload.remaining();
    int totalSize = HEADER_SIZE + payloadSize;

    ByteBuffer buffer = ByteBuffer.allocate(RESPONSE_BUFFER_CAPACITY);

    if (totalSize > buffer.capacity()) {
      throw new IllegalArgumentException(
          "Encoded message size (" + totalSize + ") exceeds buffer capacity ("
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
