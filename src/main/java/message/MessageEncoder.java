package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {
  private static final int HEADER_SIZE = 6;
  private static final MessageEncoder INSTANCE = new MessageEncoder();

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  /**
   * 기존 버퍼풀 사용 (하위 호환성)
   */
  public ByteBuffer encode(Message message) throws InterruptedException {
    // 기본적으로 응답 버퍼 사용
    return encodeWithResponseBuffer(message);
  }

  /**
   * 응답 전용 작은 버퍼 사용 (64바이트)
   */
  public ByteBuffer encodeWithResponseBuffer(Message message) throws InterruptedException {
    ByteBuffer payload = message.getPayload();
    int totalSize = HEADER_SIZE + payload.remaining();

    // 작은 응답 버퍼 사용 (64바이트면 충분)
    if (totalSize > 64) {
      throw new IllegalArgumentException("Response too large for response buffer: " + totalSize + " bytes");
    }

    ByteBuffer buffer = BufferPool.getInstance().acquireResponseBuffer();

    buffer.clear();
    buffer.putInt(payload.remaining());      // 4바이트: payload 길이
    buffer.putShort(message.getTypeValue()); // 2바이트: 메시지 타입
    buffer.put(payload);                     // 페이로드 (해시 응답의 경우 48바이트)
    buffer.flip();

    return buffer;
  }
}
