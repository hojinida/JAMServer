package main.java.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageDecoder {
  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 256;

  private static final MessageDecoder INSTANCE = new MessageDecoder();

  private static final ThreadLocal<List<Message>> MESSAGE_LIST =
      ThreadLocal.withInitial(() -> new ArrayList<>(4));

  public static MessageDecoder getInstance() {
    return INSTANCE;
  }

  public List<Message> decode(ByteBuffer buffer) {
    List<Message> messages = MESSAGE_LIST.get();
    messages.clear();

    while (buffer.remaining() >= HEADER_SIZE) {
      int startPos = buffer.position();

      int length = buffer.getInt();
      short type = buffer.getShort();

      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        buffer.position(buffer.limit());
        return messages;
      }

      if (buffer.remaining() < length) {
        buffer.position(startPos);
        return messages;
      }

      int oldLimit = buffer.limit();
      buffer.limit(buffer.position() + length);
      ByteBuffer payload = buffer.slice();
      buffer.limit(oldLimit);
      buffer.position(buffer.position() + length);

      messages.add(new Message(type, payload.asReadOnlyBuffer()));
    }

    return messages;
  }
}
