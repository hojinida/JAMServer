package main.java.message;

public enum MessageType {
  BUY_ORDER((short) 1),    // 매수 주문
  SELL_ORDER((short) 2),   // 매도 주문
  ORDER_ACK((short) 3),    // 주문 접수 확인
  ORDER_REJECT((short) 4), // 주문 거부
  EXECUTION((short) 5);    // 체결 보고

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

  /**
   * 메시지 타입을 short 값으로 반환
   */
  public short getValue() {
    return value;
  }
}
