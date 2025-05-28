package main.java.message;

import java.nio.ByteBuffer;
import main.java.server.ServerConfig;

public class MessageEncoder {

  private static final MessageEncoder INSTANCE = new MessageEncoder();

  private MessageEncoder() {
  }

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  public ByteBuffer encode(Message message) {
    ByteBuffer payload = message.getPayload();
    int payloadSize = payload.remaining();
    int totalSize = ServerConfig.HEADER_SIZE + payloadSize;

    ByteBuffer buffer = ByteBuffer.allocate(ServerConfig.RESPONSE_BUFFER_CAPACITY);

    if (totalSize > buffer.capacity()) {
      throw new IllegalArgumentException(
          "Encoded message size (" + totalSize + ") exceeds buffer capacity (" + buffer.capacity()
              + ").");
    }

    buffer.clear();
    buffer.putInt(payloadSize);
    buffer.putShort(message.getTypeValue());
    buffer.put(payload);
    buffer.flip();

    return buffer;
  }
}
