package main.java.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import main.java.message.BufferPool;
import main.java.message.Message;
import main.java.message.MessageEncoder;
import main.java.message.MessageParser;

public class Session implements AutoCloseable {
  // 세션 상태
  private final SocketChannel channel;
  private final SelectionKey key;
  private final MessageParser parser;
  private final ByteBuffer readBuffer;
  private final Queue<Message> incomingMessages; // 수신된 메시지 큐


  // 세션 ID와 생성 시간
  private final String sessionId;
  private final long creationTime;

  /**
   * 새 세션 생성
   */
  public Session(SocketChannel channel, SelectionKey key) {
    this(channel, key, 0); // 크기 무시됨
  }

  /**
   * 읽기 버퍼 크기를 지정하여 새 세션 생성 (호환성을 위해 유지)
   */
  public Session(SocketChannel channel, SelectionKey key, int readBufferSize) {
    if (channel == null || key == null) {
      throw new IllegalArgumentException("Channel and key cannot be null");
    }

    this.channel = channel;
    this.key = key;
    this.parser = new MessageParser();
    this.readBuffer = BufferPool.getInstance().acquire();
    this.incomingMessages = new ConcurrentLinkedQueue<>();
    this.sessionId = generateSessionId(channel);
    this.creationTime = System.currentTimeMillis();

    // 세션을 선택 키에 첨부
    key.attach(this);
  }

  /**
   * 고유한 세션 ID 생성
   */
  private String generateSessionId(SocketChannel channel) {
    try {
      return String.format("Session-%s-%d",
          channel.getRemoteAddress().toString().replaceAll("[^\\w.-]", "-"),
          System.nanoTime() % 10000);
    } catch (IOException e) {
      return String.format("Session-%d", System.nanoTime());
    }
  }

  /**
   * 채널에서 데이터 읽기
   *
   * @return 읽은 바이트 수 또는 EOF인 경우 -1
   */
  public int read() throws IOException {
    // 읽기 버퍼 초기화
    readBuffer.clear();

    // 채널에서 데이터 읽기
    int bytesRead = channel.read(readBuffer);

    if (bytesRead > 0) {
      // 읽기 버퍼를 읽기 모드로 전환
      readBuffer.flip();

      // 파서에 데이터 추가
      parser.addData(readBuffer);

      // 완전한 메시지 파싱
      Message message;
      while ((message = parser.nextMessage()) != null) {
        incomingMessages.offer(message);
      }
    }

    return bytesRead;
  }

  /**
   * 다음 처리 대기 중인 메시지 반환
   */
  public Message nextMessage() {
    return incomingMessages.poll();
  }

  /**
   * 메시지 전송
   */
  public void sendMessage(Message message) throws IOException {
    if (message == null || channel == null || !channel.isOpen()) {
      return;
    }

    // 메시지 인코딩
    ByteBuffer buffer = MessageEncoder.encode(message);

    try {
      // 메시지 전송 (간단한 구현, 실제로는 비동기 쓰기 큐를 사용해야 함)
      while (buffer.hasRemaining()) {
        channel.write(buffer);
      }
    } finally {
      // 버퍼 반환
      BufferPool.getInstance().release(buffer);
    }
  }

  /**
   * 세션 ID 반환
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * 세션 생성 시간 반환
   */
  public long getCreationTime() {
    return creationTime;
  }

  /**
   * 채널 반환
   */
  public SocketChannel getChannel() {
    return channel;
  }

  /**
   * 선택 키 반환
   */
  public SelectionKey getKey() {
    return key;
  }

  /**
   * 세션 종료
   */
  @Override
  public void close() {
    try {
      key.cancel();
      channel.close();

      // 버퍼 반환
      if (readBuffer != null) {
        BufferPool.getInstance().release(readBuffer);
      }

      // 파서 리소스 정리
      parser.close();

    } catch (IOException e) {
      // 무시
    }
  }
}
