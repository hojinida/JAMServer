package main.java.message;

import java.nio.ByteBuffer;

public class Message {

  // 메시지 헤더 필드
  private final int length;
  private final short type;

  // 메시지 페이로드
  private final ByteBuffer payload;

  /**
   * 새 메시지 생성
   */
  public Message(short type, ByteBuffer payload) {
    if (payload == null) {
      throw new IllegalArgumentException("Payload cannot be null");
    }

    this.length = payload.remaining();
    this.type = type;

    // 페이로드 복사 방지를 위해 원본 버퍼의 뷰 생성
    this.payload = payload.duplicate();
  }

  /**
   * MessageType Enum을 사용한 생성자
   */
  public Message(MessageType type, ByteBuffer payload) {
    this(type.getValue(), payload);
  }

  /**
   * 내부 생성자 (디코딩용)
   */
  Message(int length, short type, ByteBuffer payload) {
    this.length = length;
    this.type = type;
    this.payload = payload;
  }

  /**
   * 메시지 길이 반환
   */
  public int getLength() {
    return length;
  }

  /**
   * 메시지 타입 반환 (short 값)
   */
  public short getTypeValue() {
    return type;
  }


  /**
   * 메시지 페이로드 반환
   */
  public ByteBuffer getPayload() {
    // 원본 버퍼의 내용을 보호하기 위해 복제본 반환
    return payload.duplicate();
  }
}
