package main.java.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageDecoder {

  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 256;
  private static final MessageDecoder INSTANCE = new MessageDecoder();

  private MessageDecoder() {}

  public static MessageDecoder getInstance() {
    return INSTANCE;
  }

  public static class DecodeException extends Exception {
    public DecodeException(String message) {
      super(message);
    }
  }

  public List<Message> decode(ByteBuffer buffer) throws DecodeException {
    List<Message> messages = new ArrayList<>();

    while (buffer.remaining() >= HEADER_SIZE) {
      int startPos = buffer.position();
      int length = buffer.getInt();
      short typeValue = buffer.getShort();

      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        throw new DecodeException("Invalid message length: " + length);
      }

      if (!isValidMessageType(typeValue)) {
        throw new DecodeException("Invalid message type: " + typeValue);
      }

      if (buffer.remaining() < length) {
        buffer.position(startPos);
        break;
      }

      try {
        ByteBuffer payload = extractPayload(buffer, length);
        messages.add(new Message(typeValue, payload));
      } catch (Exception e) {
        throw new DecodeException("Failed to create message: " + e.getMessage());
      }
    }

    return messages;
  }

  private boolean isValidMessageType(short typeValue) {
    try {
      MessageType.fromValue(typeValue);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private ByteBuffer extractPayload(ByteBuffer buffer, int length) {
    int oldLimit = buffer.limit();
    buffer.limit(buffer.position() + length);
    ByteBuffer payload = buffer.slice().asReadOnlyBuffer();
    buffer.limit(oldLimit);
    buffer.position(buffer.position() + length);
    return payload;
  }
}
