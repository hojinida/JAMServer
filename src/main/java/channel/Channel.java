package main.java.channel;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import main.java.handler.business.HashRequestHandler;
import main.java.message.Message;
import main.java.message.MessageDecoder;
import main.java.message.MessageEncoder;
import main.java.util.ConnectionManager;
import main.java.util.buffer.BufferPool;

public class Channel implements Closeable {
  private static final AtomicLong CHANNEL_ID_GENERATOR = new AtomicLong(0);

  private final long channelId;
  private final SocketChannel socketChannel;
  private final SelectionKey selectionKey;

  // 전용 Read 버퍼 (채널당 하나, 1024바이트)
  private final ByteBuffer dedicatedReadBuffer;

  // Write 큐 (응답용 소형 버퍼들)
  private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean active = new AtomicBoolean(false);
  private final AtomicBoolean writeRegistered = new AtomicBoolean(false);

  // 공유 객체들
  private final MessageDecoder decoder;
  private final MessageEncoder encoder;
  private final HashRequestHandler businessHandler;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey,
      MessageDecoder decoder, MessageEncoder encoder,
      HashRequestHandler businessHandler) throws InterruptedException {

    this.channelId = CHANNEL_ID_GENERATOR.incrementAndGet();
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.decoder = decoder;
    this.encoder = encoder;
    this.businessHandler = businessHandler;

    // 전용 Read 버퍼 할당 (채널 생명주기 동안 유지)
    this.dedicatedReadBuffer = BufferPool.getInstance().acquireReadBuffer();

    System.out.println("Channel #" + channelId + " created with dedicated 1024-byte read buffer");
  }

  public void activate() {
    if (active.compareAndSet(false, true)) {
      System.out.println("Channel #" + channelId + " activated");
    }
  }

  public boolean isActive() {
    return active.get() && socketChannel.isOpen() && selectionKey.isValid();
  }

  public void handleRead() throws IOException {
    if (!active.get()) {
      return;
    }

    int bytesRead = socketChannel.read(dedicatedReadBuffer);

    if (bytesRead == -1) {
      System.out.println("Channel #" + channelId + " connection closed by peer");
      close();
      return;
    }

    if (bytesRead > 0) {
      dedicatedReadBuffer.flip();

      try {
        List<Message> messages = decoder.decode(dedicatedReadBuffer);

        for (Message message : messages) {
          businessHandler.handle(message, this);
        }

      } catch (Exception e) {
        System.err.println("Channel #" + channelId + " error processing messages: " + e.getMessage());
        close();
      } finally {
        dedicatedReadBuffer.compact(); // 남은 데이터를 버퍼 시작으로 이동
      }
    }
  }

  /**
   * 비동기 Write - 작은 응답 버퍼를 큐에 추가
   */
  public void write(Message message) {
    if (!isActive()) {
      return;
    }

    try {
      // 작은 응답 버퍼 사용 (64바이트)
      ByteBuffer encoded = encoder.encodeWithResponseBuffer(message);

      writeQueue.offer(encoded);

      // WRITE 이벤트 등록
      if (writeRegistered.compareAndSet(false, true) && selectionKey.isValid()) {
        int currentOps = selectionKey.interestOps();
        selectionKey.interestOps(currentOps | SelectionKey.OP_WRITE);
        selectionKey.selector().wakeup();
      }

    } catch (Exception e) {
      System.err.println("Channel #" + channelId + " error in write: " + e.getMessage());
      close();
    }
  }

  public void handleWrite() throws IOException {
    if (!active.get()) {
      return;
    }

    flush();
  }

  private void flush() throws IOException {
    int buffersProcessed = 0;

    ByteBuffer buffer;
    while ((buffer = writeQueue.peek()) != null) {
      long written = socketChannel.write(buffer);

      if (buffer.hasRemaining()) {
        // 소켓 버퍼가 가득 참, 나중에 다시 시도
        return;
      }

      // 버퍼 완전히 전송됨
      writeQueue.poll();
      buffersProcessed++;

      // 작은 응답 버퍼 반환
      BufferPool.getInstance().releaseResponseBuffer(buffer);
    }

    // 모든 데이터 전송 완료
    if (writeQueue.isEmpty() && selectionKey.isValid() && writeRegistered.compareAndSet(true, false)) {
      int currentOps = selectionKey.interestOps();
      selectionKey.interestOps(currentOps & ~SelectionKey.OP_WRITE);
    }
  }

  @Override
  public String toString() {
    try {
      return "Channel{id=" + channelId +
          ", remote=" + socketChannel.getRemoteAddress() +
          ", active=" + active.get() +
          ", writeQueue=" + writeQueue.size() + "}";
    } catch (IOException e) {
      return "Channel{id=" + channelId + ", remote=unknown}";
    }
  }

  @Override
  public void close() {
    if (active.compareAndSet(true, false)) {
      System.out.println("Channel #" + channelId + " closing");

      try {
        if (selectionKey.isValid()) {
          selectionKey.cancel();
        }
        socketChannel.close();
      } catch (IOException e) {
        System.err.println("Channel #" + channelId + " error closing socket: " + e.getMessage());
      } finally {
        // 전용 Read 버퍼 반환
        if (dedicatedReadBuffer != null) {
          BufferPool.getInstance().releaseReadBuffer(dedicatedReadBuffer);
        }

        // Write 큐의 응답 버퍼들 반환
        ByteBuffer buffer;
        int releasedCount = 0;
        while ((buffer = writeQueue.poll()) != null) {
          BufferPool.getInstance().releaseResponseBuffer(buffer);
          releasedCount++;
        }

        if (releasedCount > 0) {
          System.out.println("Channel #" + channelId + " released " + releasedCount + " pending response buffers");
        }

        ConnectionManager.decrement();
        System.out.println("Channel #" + channelId + " closed and cleaned up");
      }
    }
  }
}
