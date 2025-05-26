package main.java.message;

import java.util.HashMap;
import java.util.Map;

public enum MessageType {
  HASH_REQUEST((short) 1),
  HASH_RESPONSE((short) 2);

  private final short value;
  private static final Map<Short, MessageType> VALUE_CACHE = new HashMap<>();

  static {
    for (MessageType type : values()) {
      VALUE_CACHE.put(type.value, type);
    }
  }

  MessageType(short value) {
    this.value = value;
  }

  public static MessageType fromValue(short value) {
    MessageType type = VALUE_CACHE.get(value);
    if (type == null) {
      throw new IllegalArgumentException("Unknown message type: " + value);
    }
    return type;
  }

  public short getValue() {
    return value;
  }
}
