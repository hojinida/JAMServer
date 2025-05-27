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
  private final HashRequestHandler businessHandler;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey,
      MessageDecoder decoder, MessageEncoder encoder,
      HashRequestHandler businessHandler) throws InterruptedException {

    this.channelId = CHANNEL_ID_GENERATOR.incrementAndGet();
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.decoder = decoder;
    this.businessHandler = businessHandler;
    this.dedicatedReadBuffer = BufferPool.getInstance().acquireReadBuffer();
  }

  public void activate() {
    if (active.compareAndSet(false, true)) {
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
        dedicatedReadBuffer.compact();
      }
    }
  }

  public void writeDirectBuffer(ByteBuffer buffer) {
    if (!isActive()) {
      if (buffer != null) {
        BufferPool.getInstance().releaseResponseBuffer(buffer);
      }
      return;
    }

    try {
      writeQueue.offer(buffer);
      registerWriteInterest();

    } catch (Exception e) {
      System.err.println("Channel #" + channelId + " error in writeDirectBuffer: " + e.getMessage());
      if (buffer != null) {
        BufferPool.getInstance().releaseResponseBuffer(buffer);
      }
      close();
    }
  }

  private void registerWriteInterest() {
    if (writeRegistered.compareAndSet(false, true) && selectionKey.isValid()) {
      int currentOps = selectionKey.interestOps();
      selectionKey.interestOps(currentOps | SelectionKey.OP_WRITE);
      selectionKey.selector().wakeup();
    }
  }

  public void handleWrite() throws IOException {
    if (!active.get()) {
      return;
    }
    flush();
  }

  private void flush() throws IOException {
    ByteBuffer buffer;
    while ((buffer = writeQueue.peek()) != null) {
      socketChannel.write(buffer);

      if (buffer.hasRemaining()) {
        return;
      }

      writeQueue.poll();

      BufferPool.getInstance().releaseResponseBuffer(buffer);
    }

    if (writeQueue.isEmpty() && selectionKey.isValid() && writeRegistered.compareAndSet(true, false)) {
      int currentOps = selectionKey.interestOps();
      selectionKey.interestOps(currentOps & ~SelectionKey.OP_WRITE);
    }
  }

  @Override
  public void close() {
    if (active.compareAndSet(true, false)) {
      try {
        if (selectionKey.isValid()) {
          selectionKey.cancel();
        }
        socketChannel.close();
      } catch (IOException e) {
        System.err.println("Channel #" + channelId + " error closing socket: " + e.getMessage());
      } finally {
        if (dedicatedReadBuffer != null) {
          BufferPool.getInstance().releaseReadBuffer(dedicatedReadBuffer);
        }

        ByteBuffer buffer;
        while ((buffer = writeQueue.poll()) != null) {
          BufferPool.getInstance().releaseResponseBuffer(buffer);
        }

        ConnectionManager.decrement();
      }
    }
  }
}
