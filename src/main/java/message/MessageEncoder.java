package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {
  private static final int HEADER_SIZE = 6;

  // 싱글톤
  private static final MessageEncoder INSTANCE = new MessageEncoder();

  private MessageEncoder() {}

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  // Stateless 인코딩
  public ByteBuffer encode(Message message) {
    ByteBuffer payload = message.getPayload();
    int length = payload.remaining();

    // 버퍼 할당 (여기서는 간단하게 일반 버퍼 사용)
    ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + length);

    // 헤더 쓰기
    buffer.putInt(length);
    buffer.putShort(message.getTypeValue());

    // 페이로드 쓰기
    buffer.put(payload);

    buffer.flip();
    return buffer;
  }
}
