package main.java.message;

import java.nio.ByteBuffer;

public class Message {
  private final short type;
  private final ByteBuffer payload;

  public Message(short type, ByteBuffer payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }
    this.type = type;
    this.payload = payload.asReadOnlyBuffer();
  }

  public short getTypeValue() {
    return type;
  }

  public MessageType getType() {
    return MessageType.fromValue(type);
  }

  public ByteBuffer getPayload() {
    return payload.asReadOnlyBuffer();
  }
}
