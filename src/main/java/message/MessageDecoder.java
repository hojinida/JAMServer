package main.java.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageDecoder {
  private static final int HEADER_SIZE = 6;
  private static final int MAX_PAYLOAD_SIZE = 256;

  private static final MessageDecoder INSTANCE = new MessageDecoder();

  public static MessageDecoder getInstance() {
    return INSTANCE;
  }

  // Zero-copy 디코딩 - 직접 버퍼에서 파싱
  public List<Message> decode(ByteBuffer buffer) {
    List<Message> messages = new ArrayList<>();

    while (buffer.remaining() >= HEADER_SIZE) {
      // 현재 위치 저장
      int startPos = buffer.position();

      // 헤더 읽기
      int length = buffer.getInt();
      short type = buffer.getShort();

      // 유효성 검사
      if (length < 0 || length > MAX_PAYLOAD_SIZE) {
        buffer.position(buffer.limit());  // 버퍼 끝으로
        return messages;
      }

      // 페이로드 확인
      if (buffer.remaining() < length) {
        buffer.position(startPos);  // 되돌리기
        return messages;
      }

      // Zero-copy: slice로 뷰 생성 (복사 없음!)
      int oldLimit = buffer.limit();
      buffer.limit(buffer.position() + length);
      ByteBuffer payload = buffer.slice();
      buffer.limit(oldLimit);
      buffer.position(buffer.position() + length);

      // 읽기 전용 뷰로 메시지 생성
      messages.add(new Message(type, payload.asReadOnlyBuffer()));
    }

    return messages;
  }
}
