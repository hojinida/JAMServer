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
import main.java.handler.business.HashRequestHandler;
import main.java.message.Message;
import main.java.message.MessageDecoder;
import main.java.message.MessageEncoder;
import main.java.util.ConnectionManager;
import main.java.util.buffer.BufferPool;

public class Channel implements Closeable {
  private final SocketChannel socketChannel;
  private final SelectionKey selectionKey;

  private final ByteBuffer readBuffer;
  private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean active = new AtomicBoolean(false);
  private final AtomicBoolean writeInProgress = new AtomicBoolean(false);

  // 공유 객체들
  private final MessageDecoder decoder;
  private final MessageEncoder encoder;
  private final HashRequestHandler businessHandler;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey,
      MessageDecoder decoder, MessageEncoder encoder,
      HashRequestHandler businessHandler) throws InterruptedException {
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.decoder = decoder;
    this.encoder = encoder;
    this.businessHandler = businessHandler;

    this.readBuffer = BufferPool.getInstance().acquire();
  }

  public void activate() {
    if (active.compareAndSet(false, true)) {
      // 단순 활성화
    }
  }

  public void handleRead() throws IOException {
    int bytesRead = socketChannel.read(readBuffer);

    if (bytesRead == -1) {
      close();
      return;
    }

    if (bytesRead > 0) {
      readBuffer.flip();

      try {
        List<Message> messages = decoder.decode(readBuffer);

        for (Message message : messages) {
          businessHandler.handle(message, this);
        }
      } finally {
        readBuffer.compact();
      }
    }
  }

  public void write(Message message) {
    try {
      ByteBuffer encoded = encoder.encode(message);
      writeQueue.offer(encoded);

      if (writeInProgress.compareAndSet(false, true)) {
        try {
          flush();
        } catch (IOException e) {
          close();
        } finally {
          writeInProgress.set(false);

          if (!writeQueue.isEmpty()) {
            selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            selectionKey.selector().wakeup();
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      close();
    }
  }

  public void handleWrite() throws IOException {
    if (writeInProgress.compareAndSet(false, true)) {
      try {
        flush();
      } finally {
        writeInProgress.set(false);
      }
    }
  }

  private void flush() throws IOException {
    ByteBuffer buffer;
    while ((buffer = writeQueue.peek()) != null) {
      int written = socketChannel.write(buffer);

      if (buffer.hasRemaining()) {
        selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        break;
      }

      writeQueue.poll();
      if (buffer.isDirect() && buffer.capacity() == 1024) {
        BufferPool.getInstance().release(buffer);
      }
    }

    if (writeQueue.isEmpty() && selectionKey.isValid()) {
      selectionKey.interestOps(SelectionKey.OP_READ);
    }
  }

  @Override
  public void close() {
    if (active.compareAndSet(true, false)) {
      try {
        selectionKey.cancel();
        socketChannel.close();
      } catch (IOException e) {
        // 무시
      } finally {
        // 버퍼 반환
        BufferPool.getInstance().release(readBuffer);

        // 쓰기 큐 버퍼들도 반환
        ByteBuffer buffer;
        while ((buffer = writeQueue.poll()) != null) {
          if (buffer.isDirect()) {
            BufferPool.getInstance().release(buffer);
          }
        }

        ConnectionManager.decrement();
      }
    }
  }
}
