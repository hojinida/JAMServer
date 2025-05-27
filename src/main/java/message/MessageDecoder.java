package main.java.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageDecoder {
  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 256;
  private static final int MAX_RECOVERY_ATTEMPTS = 100;

  private static final MessageDecoder INSTANCE = new MessageDecoder();

  private final List<Message> messageList = new ArrayList<>(4);

  public static MessageDecoder getInstance() {
    return INSTANCE;
  }

  public List<Message> decode(ByteBuffer buffer) {
    messageList.clear();

    while (buffer.remaining() >= HEADER_SIZE) {
      int startPos = buffer.position();

      int length = buffer.getInt();
      short type = buffer.getShort();

      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        if (!attemptRecovery(buffer, startPos)) {
          buffer.position(buffer.limit());
          break;
        }
        continue;
      }

      if (!isValidMessageType(type)) {
        if (!attemptRecovery(buffer, startPos)) {
          buffer.position(buffer.limit());
          break;
        }
        continue;
      }

      if (buffer.remaining() < length) {
        buffer.position(startPos);
        break;
      }

      try {
        int oldLimit = buffer.limit();
        buffer.limit(buffer.position() + length);
        ByteBuffer payload = buffer.slice();
        buffer.limit(oldLimit);
        buffer.position(buffer.position() + length);

        messageList.add(new Message(type, payload.asReadOnlyBuffer()));

      } catch (Exception e) {
        System.err.println("Failed to create message: " + e.getMessage());
        buffer.position(startPos);
        if (!attemptRecovery(buffer, startPos)) {
          buffer.position(buffer.limit());
          break;
        }
      }
    }

    return messageList;
  }

  private boolean isValidMessageType(short type) {
    try {
      MessageType.fromValue(type);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean attemptRecovery(ByteBuffer buffer, int errorPos) {
    int originalPosition = buffer.position();
    int searchStart = errorPos + 1;
    int searchEnd = Math.min(errorPos + MAX_RECOVERY_ATTEMPTS, buffer.limit() - HEADER_SIZE);

    for (int pos = searchStart; pos <= searchEnd; pos++) {
      buffer.position(pos);

      if (buffer.remaining() < HEADER_SIZE) {
        break;
      }

      int testLength = buffer.getInt(pos);
      short testType = buffer.getShort(pos + 4);

      if (testLength >= 0 && testLength <= MAX_PAYLOAD_SIZE && isValidMessageType(testType)) {
        if (pos + HEADER_SIZE + testLength <= buffer.limit()) {
          System.err.println("Recovery successful: skipped " + (pos - errorPos) + " bytes");
          buffer.position(pos);
          return true;
        }
      }
    }

    System.err.println("Recovery failed: no valid header found within " + MAX_RECOVERY_ATTEMPTS + " bytes");
    buffer.position(originalPosition);
    return false;
  }
}
