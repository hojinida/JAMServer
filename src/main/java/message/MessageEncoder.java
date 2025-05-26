package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageEncoder {
  private static final int HEADER_SIZE = 6;
  private static final MessageEncoder INSTANCE = new MessageEncoder();

  public static MessageEncoder getInstance() {
    return INSTANCE;
  }

  // BufferPool에서 버퍼 할당
  public ByteBuffer encode(Message message) throws InterruptedException {
    ByteBuffer payload = message.getPayload();
    int totalSize = HEADER_SIZE + payload.remaining();

    // BufferPool에서 버퍼 획득
    ByteBuffer buffer = BufferPool.getInstance().acquire();

    // 버퍼가 너무 작으면 일반 버퍼 사용 (큰 메시지용)
    if (buffer.capacity() < totalSize) {
      BufferPool.getInstance().release(buffer);
      buffer = ByteBuffer.allocateDirect(totalSize);
    }

    buffer.clear();
    buffer.putInt(payload.remaining());
    buffer.putShort(message.getTypeValue());
    buffer.put(payload);
    buffer.flip();

    return buffer;
  }
}
