package main.java.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageDecoder {

  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 256;

  // 싱글톤
  private static final MessageDecoder INSTANCE = new MessageDecoder();

  private MessageDecoder() {}

  public static MessageDecoder getInstance() {
    return INSTANCE;
  }

  // Stateless 디코딩 - accumulator는 외부에서 관리
  public List<Message> decode(ByteBuffer accumulator, ByteBuffer input) {
    List<Message> messages = new ArrayList<>();

    // 새 데이터를 accumulator에 추가
    accumulator.put(input);
    accumulator.flip();

    // 완전한 메시지들 추출
    while (accumulator.remaining() >= HEADER_SIZE) {
      int startPos = accumulator.position();

      // 헤더 읽기
      int length = accumulator.getInt();
      short type = accumulator.getShort();

      // 유효성 검사
      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        // 잘못된 메시지 - 연결 종료 필요
        accumulator.clear();
        return messages;
      }

      if (accumulator.remaining() < length) {
        // 아직 전체 페이로드가 도착하지 않음
        accumulator.position(startPos);
        break;
      }

      // 페이로드 추출 (Zero-copy)
      int payloadStart = accumulator.position();
      int payloadEnd = payloadStart + length;

      accumulator.limit(payloadEnd);
      ByteBuffer payload = accumulator.slice();

      accumulator.position(payloadEnd);
      accumulator.limit(accumulator.capacity());

      messages.add(new Message(type, payload));
    }

    accumulator.compact();
    return messages;
  }
}
