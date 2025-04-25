package main.java.message;

import java.nio.ByteBuffer;

public class MessageFactory {

  // 페이로드 필드 오프셋
  public static final int PRICE_OFFSET = 0;       // 가격 (8바이트 long)
  public static final int TIMESTAMP_OFFSET = 8;   // 타임스탬프 (8바이트 long)
  public static final int MIN_PAYLOAD_SIZE = 16;  // 최소 페이로드 크기

  private MessageFactory() {
    // 유틸리티 클래스이므로 인스턴스화 방지
  }

  /**
   * 주문 메시지 생성 팩토리 메서드
   */
  public static Message createOrderMessage(MessageType type, long price) {
    ByteBuffer payload = BufferPool.getInstance().acquire();

    try {
      payload.putLong(PRICE_OFFSET, price);
      payload.putLong(TIMESTAMP_OFFSET, System.currentTimeMillis());
      payload.flip();

      return new Message(type, payload);
    } catch (Exception e) {
      BufferPool.getInstance().release(payload);
      throw e;
    }
  }

  /**
   * 매수 주문 메시지 생성
   */
  public static Message createBuyOrder(long price) {
    return createOrderMessage(MessageType.BUY_ORDER, price);
  }

  /**
   * 매도 주문 메시지 생성
   */
  public static Message createSellOrder(long price) {
    return createOrderMessage(MessageType.SELL_ORDER, price);
  }

  /**
   * 체결 메시지 생성
   */
  public static Message createExecution(long price) {
    return createOrderMessage(MessageType.EXECUTION, price);
  }

  /**
   * 주문 확인 메시지 생성
   */
  public static Message createOrderAck(long price) {
    return createOrderMessage(MessageType.ORDER_ACK, price);
  }

  /**
   * 주문 거부 메시지 생성
   */
  public static Message createOrderReject(long price) {
    return createOrderMessage(MessageType.ORDER_REJECT, price);
  }
}
