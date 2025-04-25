package main.java.message;

import java.nio.ByteBuffer;

public class MessageDecoder {

  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 30;

  private MessageDecoder() {
  }

  public static Message decode(ByteBuffer buffer) {
    if (buffer == null || buffer.remaining() < HEADER_SIZE) {
      return null;
    }

    int initialPosition = buffer.position();

    try {
      int length = buffer.getInt();
      short type = buffer.getShort();

      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        buffer.position(initialPosition);
        return null;
      }

      // 완전한 메시지가 있는지 확인
      if (buffer.remaining() < length) {
        buffer.position(initialPosition);
        return null;
      }

      // 페이로드 추출 - slice() 사용하여 메모리 복사 회피
      int payloadStart = buffer.position();
      int payloadEnd = payloadStart + length;

      // 원본 버퍼의 한계 저장
      int originalLimit = buffer.limit();

      // 페이로드 슬라이스 생성
      buffer.limit(payloadEnd);
      ByteBuffer payload = buffer.slice();

      // 버퍼 위치와 한계 복원
      buffer.position(payloadEnd);
      buffer.limit(originalLimit);

      return new Message(length, type, payload);
    } catch (Exception e) {
      // 예외 발생 시 버퍼 위치 복원
      buffer.position(initialPosition);
      return null;
    }
  }
}
