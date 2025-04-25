package main.java.message;

import java.nio.ByteBuffer;

public class MessageDecoder {

  // 메시지 헤더 구조
  private static final int HEADER_SIZE = 6; // 길이(4) + 타입(2)
  private static final int MAX_PAYLOAD_SIZE = 1024; // 1KB 제한 (매도/매수 주문에 충분)

  private MessageDecoder() {
    // 유틸리티 클래스이므로 인스턴스화 방지
  }

  /**
   * 와이어 포맷에서 메시지 디코딩
   */
  public static Message decode(ByteBuffer buffer) {
    if (buffer == null || buffer.remaining() < HEADER_SIZE) {
      return null;
    }

    // 버퍼 위치 저장
    int initialPosition = buffer.position();

    try {
      // 헤더 읽기
      int length = buffer.getInt();
      short type = buffer.getShort();

      // 유효성 검사
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
