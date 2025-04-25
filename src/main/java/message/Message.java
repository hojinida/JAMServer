package main.java.message;

import java.nio.ByteBuffer;

public class Message {

  private final int length;
  private final short type;

  private final ByteBuffer payload;

  public Message(short type, ByteBuffer payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }

    this.length = payload.remaining();
    this.type = type;

    this.payload = payload.duplicate();
  }

  public Message(MessageType type, ByteBuffer payload) {
    this(type.getValue(), payload);
  }

  Message(int length, short type, ByteBuffer payload) {
    this.length = length;
    this.type = type;
    this.payload = payload;
  }

  public int getLength() {
    return length;
  }

  public short getTypeValue() {
    return type;
  }

  public MessageType getType() {
    return MessageType.fromValue(type);
  }

  public ByteBuffer getPayload() {
    return payload.duplicate();
  }
}
