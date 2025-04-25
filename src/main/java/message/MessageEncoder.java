package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {

  // 메시지 헤더 구조
  private static final int HEADER_SIZE = 6; // 길이(4) + 타입(2)

  private MessageEncoder() {
    // 유틸리티 클래스이므로 인스턴스화 방지
  }

  /**
   * 메시지를 와이어 포맷으로 인코딩
   */
  public static ByteBuffer encode(Message message) throws InterruptedException {
    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }

    int length = message.getLength();
    ByteBuffer payload = message.getPayload();

    // 인코딩을 위한 버퍼 획득 (단일 풀에서)
    ByteBuffer buffer = BufferPool.getInstance().acquire();

    try {
      // 헤더 작성
      buffer.putInt(length);
      buffer.putShort(message.getTypeValue());

      // 페이로드 복사 - 효율적인 벌크 복사 사용
      ByteBuffer payloadDup = payload.duplicate();
      buffer.put(payloadDup);

      // 버퍼를 읽기 모드로 전환
      buffer.flip();
      return buffer;
    } catch (Exception e) {
      // 예외 발생 시 버퍼 반환
      BufferPool.getInstance().release(buffer);
      throw e;
    }
  }
}
