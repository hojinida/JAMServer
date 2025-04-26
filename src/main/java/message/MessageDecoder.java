package main.java.message;

import java.nio.ByteBuffer;

public class MessageDecoder {

  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 30;

  private MessageDecoder() {
  }

  public static Message decode(ByteBuffer buffer) {
    if (buffer == null || buffer.remaining() < HEADER_SIZE) {
      return null;
    }

    int initialPosition = buffer.position();

    try {
      int length = buffer.getInt();
      short type = buffer.getShort();

      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        buffer.position(initialPosition);
        return null;
      }

      if (buffer.remaining() < length) {
        buffer.position(initialPosition);
        return null;
      }

      int payloadStart = buffer.position();
      int payloadEnd = payloadStart + length;

      int originalLimit = buffer.limit();

      buffer.limit(payloadEnd);
      ByteBuffer payload = buffer.slice();

      buffer.position(payloadEnd);
      buffer.limit(originalLimit);

      return new Message(length, type, payload);
    } catch (Exception e) {
      buffer.position(initialPosition);
      return null;
    }
  }
}
