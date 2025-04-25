package main.java.message;

import java.nio.ByteBuffer;

public class MessageParser implements AutoCloseable {

  // 백프레셔 제한값
  private static final int HIGH_WATERMARK = 128 * 1024; // 128KB

  // 파싱 상태
  private ByteBuffer buffer;
  private boolean backpressureActive = false;

  /**
   * 메시지 파서 생성
   */
  public MessageParser() {
    this.buffer = BufferPool.getInstance().acquire();
  }

  /**
   * 메시지 파서 생성 (초기 크기 지정 - 호환성을 위해 유지)
   */
  public MessageParser(int initialSize) {
    this.buffer = BufferPool.getInstance().acquire();
  }

  /**
   * 데이터 추가
   * @return 백프레셔 활성화 여부 (true인 경우 데이터 수신 중단 권장)
   */
  public boolean addData(ByteBuffer data) {
    if (buffer.position() + data.remaining() > HIGH_WATERMARK) {
      backpressureActive = true;
    }

    if (buffer.remaining() < data.remaining()) {
      expandBuffer(buffer.position() + data.remaining());
    }

    buffer.put(data);

    return backpressureActive;
  }

  /**
   * 버퍼 확장
   */
  private void expandBuffer(int minCapacity) {
    // 현재 버퍼 크기로 충분하지 않을 경우에만 새 버퍼 생성
    if (buffer.capacity() < minCapacity) {
      // 새 버퍼 획득
      ByteBuffer newBuffer = BufferPool.getInstance().acquire();

      // 현재 버퍼가 새 버퍼보다 크면, 더 큰 버퍼를 직접 할당해야 함
      if (minCapacity > newBuffer.capacity()) {
        // 단일 크기 버퍼 풀 사용 시 이런 상황은 드물지만,
        // 안전을 위해 예외 상황 처리
        throw new RuntimeException("필요한 버퍼 크기(" + minCapacity +
            "바이트)가 풀 버퍼 크기보다 큽니다. 시스템 재구성이 필요합니다.");
      }

      // 현재 버퍼의 데이터를 새 버퍼로 복사
      buffer.flip();
      newBuffer.put(buffer);

      // 기존 버퍼 반환 및 새 버퍼로 교체
      ByteBuffer oldBuffer = buffer;
      buffer = newBuffer;
      BufferPool.getInstance().release(oldBuffer);
    }
  }

  /**
   * 다음 완전한 메시지 추출
   */
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

  /**
   * 현재 처리 중인 데이터의 양을 반환
   */
  public int getPendingDataSize() {
    return buffer.position();
  }

  /**
   * 백프레셔 상태 확인
   */
  public boolean isBackpressureActive() {
    return backpressureActive;
  }

  /**
   * 리소스 정리
   */
  @Override
  public void close() {
    if (buffer != null) {
      BufferPool.getInstance().release(buffer);
      buffer = null;
    }
  }
}
