package main.java.message;

public enum MessageType {
  HASH_REQUEST((short) 1),
  HASH_RESPONSE((short) 2);

  private final short value;

  MessageType(short value) {
    this.value = value;
  }

  public static MessageType fromValue(short value) {
    for (MessageType type : MessageType.values()) {
      if (type.value == value) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown message type: " + value);
  }

  public short getValue() {
    return value;
  }
}
