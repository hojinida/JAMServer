package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageParser implements AutoCloseable {

  private static final int HIGH_WATERMARK = 128 * 1024; // 128KB

  private ByteBuffer buffer;
  private boolean backpressureActive = false;

  public MessageParser() throws InterruptedException {
    this.buffer = BufferPool.getInstance().acquire();
  }

  public boolean addData(ByteBuffer data) {
    if (buffer.position() + data.remaining() > HIGH_WATERMARK) {
      backpressureActive = true;
    }

    if (buffer.remaining() < data.remaining()) {
      throw new IllegalStateException("버퍼 공간이 부족합니다. 현재: " + buffer.remaining() +
          ", 필요: " + data.remaining());
    }

    buffer.put(data);
    return backpressureActive;
  }

  public Message nextMessage() {
    // 현재 버퍼에서 읽기 모드로 전환
    buffer.flip();

    // 메시지 디코딩 시도
    Message message = MessageDecoder.decode(buffer);

    if (message != null) {
      // 메시지를 성공적으로 추출했으므로 버퍼 컴팩트
      buffer.compact();

      // 데이터가 충분히 처리되었다면 백프레셔 해제
      if (buffer.position() < HIGH_WATERMARK / 2) { // 히스테리시스 적용
        backpressureActive = false;
      }
    } else {
      // 완전한 메시지가 없음 - 버퍼 상태 복원
      buffer.position(buffer.limit());
      buffer.limit(buffer.capacity());
    }

    return message;
  }

  public int getPendingDataSize() {
    return buffer.position();
  }

  public boolean isBackpressureActive() {
    return backpressureActive;
  }

  @Override
  public void close() throws InterruptedException {
    if (buffer != null) {
      BufferPool.getInstance().release(buffer);
      buffer = null;
    }
  }
}
