package main.java.transport;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import main.java.core.ConnectionManager;
import main.java.util.BufferTimeoutException;
import main.java.util.buffer.BufferPool;
import main.java.message.Message;
import main.java.message.MessageParser;

public class Session implements Closeable { // AutoCloseable 대신 Closeable 선호 시 변경

  // 세션 상태
  private final SocketChannel channel;
  private final SelectionKey key;
  private final MessageParser parser; // 파서가 내부 버퍼 관리

  // 세션 ID와 생성 시간
  private final String sessionId;
  private final long creationTime;

  /**
   * 새 세션 생성. BufferPool 또는 MessageParser에서 예외 발생 가능.
   *
   * @param channel SocketChannel
   * @param key SelectionKey
   * @throws BufferTimeoutException 버퍼 풀 타임아웃 시
   * @throws InterruptedException 스레드 인터럽트 시
   * @throws IOException MessageParser 생성 중 내부 오류 발생 시 (덜 일반적)
   */
  public Session(SocketChannel channel, SelectionKey key) throws BufferTimeoutException, IOException {
    if (channel == null || key == null) {
      throw new IllegalArgumentException("Channel and key cannot be null");
    }

    this.channel = channel;
    this.key = key;

    // MessageParser 생성 (내부적으로 버퍼 획득 시도)
    try {
      this.parser = new MessageParser();
    } catch (BufferTimeoutException e) {
      // 버퍼 획득 실패 시 예외를 호출자(WorkerSelector)에게 전달
      System.err.println("Failed to create MessageParser for new session (buffer acquisition): " + e.getMessage());
      throw e;
    } catch (Exception e) { // 혹시 모를 다른 예외 (예: 생성자 내부 로직)
      System.err.println("Unexpected error creating MessageParser: " + e.getMessage());
      // IOException 등으로 래핑하거나 RuntimeException으로 처리 가능
      throw new IOException("Failed to initialize MessageParser", e);
    }

    this.sessionId = generateSessionId(channel);
    this.creationTime = System.currentTimeMillis();

    // key.attach(this); // 세션 첨부는 WorkerSelector에서 수행하므로 제거
  }

  /**
   * 고유한 세션 ID 생성 (기존 방식 유지)
   */
  private String generateSessionId(SocketChannel channel) {
    try {
      // UUID 사용 예시: return channel.getRemoteAddress().toString() + "-" + UUID.randomUUID().toString().substring(0, 8);
      return String.format("Session-%s-%d",
          channel.getRemoteAddress().toString().replaceAll("[^\\w.-]", "-"),
          System.nanoTime() % 10000);
    } catch (IOException e) {
      // UUID 사용 예시: return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
      return String.format("Session-unknown-%d", System.nanoTime());
    }
  }

  /**
   * 채널에서 데이터를 읽어 파서에 전달합니다.
   *
   * @return 읽은 바이트 수 또는 EOF인 경우 -1
   * @throws IOException 채널 읽기 오류 시
   * @throws BufferTimeoutException 임시 읽기 버퍼 획득 타임아웃 시
   * @throws InterruptedException 스레드 인터럽트 시
   */
  public int read() throws IOException, BufferTimeoutException, InterruptedException {
    ByteBuffer tempReadBuffer = BufferPool.getInstance().acquire(); // 임시 버퍼 획득
    int bytesRead = -1;
    try {
      tempReadBuffer.clear(); // 버퍼 재사용 준비
      bytesRead = channel.read(tempReadBuffer);

      if (bytesRead > 0) {
        tempReadBuffer.flip(); // 읽기 모드로 전환
        parser.addData(tempReadBuffer); // 파서에 데이터 추가
      }
    } finally {
      // 사용한 임시 버퍼 반드시 반환
      BufferPool.getInstance().release(tempReadBuffer);
    }
    return bytesRead;
  }

  /**
   * 파서에서 다음 완전한 메시지를 가져옵니다.
   *
   * @return 다음 메시지 또는 없으면 null
   */
  public Message nextMessage() {
    // MessageParser가 내부적으로 디코딩 및 메시지 생성을 처리
    return parser.nextMessage();
  }

  public void sendMessage(Message message){
    // 미구현
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
   * MessageParser의 백프레셔 상태를 반환합니다.
   *
   * @return 백프레셔 활성화 여부
   */
  public boolean isBackpressureActive() {
    return parser.isBackpressureActive();
  }


  /**
   * 세션 리소스를 정리합니다. (SelectionKey, MessageParser, SocketChannel, ConnectionManager)
   * AutoCloseable/Closeable 인터페이스 구현.
   */
  @Override
  public void close() throws IOException { // Closeable 인터페이스는 기본적으로 IOException을 던짐
    System.out.println("Closing resources for session: " + sessionId);
    boolean decremented = false; // 중복 감소 방지 플래그 (필요 시)

    // 1. SelectionKey 취소
    if (key != null && key.isValid()) {
      try {
        key.cancel();
      } catch (Exception e) { // CancelException 등 가능
        System.err.println("Error cancelling key for session " + sessionId + ": " + e.getMessage());
      }
    }

    // 2. MessageParser 닫기 (내부 버퍼 반환)
    if (parser != null) {
      try {
        parser.close(); // MessageParser의 close() 호출 (AutoCloseable 구현 가정)
      } catch (Exception e) {
        System.err.println("Error closing MessageParser for session " + sessionId + ": " + e.getMessage());
      }
    }

    // 3. SocketChannel 닫기
    if (channel != null && channel.isOpen()) {
      try {
        channel.close();
      } catch (IOException e) {
        // close() 작업 중 발생한 IOException은 다시 던져서 호출자가 알 수 있도록 함
        System.err.println("Error closing channel for session " + sessionId + ": " + e.getMessage());
        // 필요하다면 여기서 IOException을 던지지 않고 로깅만 할 수도 있음
        throw e; // 또는 로깅 후 무시: // e.printStackTrace();
      }
    }

    // 4. ConnectionManager 카운터 감소 (정리 작업 마지막에 수행)
    // close()가 여러 번 호출되어도 decrement가 한 번만 호출되도록 보장하는 로직이 ConnectionManager에 있거나,
    // 여기서 상태 플래그를 사용하여 한 번만 호출되도록 할 수 있음.
    // 현재는 ConnectionManager가 중복 호출을 안전하게 처리한다고 가정.
    ConnectionManager.decrement();
    // System.out.println("Connection count decremented by session close: " + sessionId);
  }
}
