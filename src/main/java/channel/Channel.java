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

  private final ByteBuffer accumulator = ByteBuffer.allocate(1024);
  private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean active = new AtomicBoolean(false);
  private final AtomicBoolean writeInProgress = new AtomicBoolean(false);

  // 공유 객체들 (참조만)
  private final MessageDecoder decoder;
  private final MessageEncoder encoder;
  private final HashRequestHandler businessHandler;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey, MessageDecoder decoder,
      MessageEncoder encoder, HashRequestHandler businessHandler) {
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.decoder = decoder;
    this.encoder = encoder;
    this.businessHandler = businessHandler;
  }

  public void activate() {
    if (active.compareAndSet(false, true)) {
      try {
        socketChannel.configureBlocking(false);
        selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        ConnectionManager.tryIncrement();
      } catch (IOException e) {
        close();
      }
    }
  }

  public void handleRead() throws IOException, InterruptedException {
    ByteBuffer readBuffer = BufferPool.getInstance().acquire();
    try {
      int bytesRead = socketChannel.read(readBuffer);

      if (bytesRead == -1) {
        close();
        return;
      }

      if (bytesRead > 0) {
        readBuffer.flip();

        List<Message> messages = decoder.decode(accumulator, readBuffer);

        for (Message message : messages) {
          businessHandler.handle(message, this);
        }
      }
    } finally {
      BufferPool.getInstance().release(readBuffer);
    }
  }

  public void write(Message message) {
    ByteBuffer encoded = encoder.encode(message);
    writeQueue.offer(encoded);

    if (writeInProgress.compareAndSet(false, true)) {
      try {
        flush();
      } catch (IOException e) {
        close();
      } finally {
        writeInProgress.set(false);

        if (!writeQueue.isEmpty() && writeInProgress.compareAndSet(false, true)) {
          try {
            flush();
          } catch (IOException e) {
            close();
          } finally {
            writeInProgress.set(false);
          }
        }
      }
    }
  }

  public void handleWrite() throws IOException {
    flush();
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
    }

    if (writeQueue.isEmpty() && selectionKey.isValid()) {
      selectionKey.interestOps(SelectionKey.OP_READ);
    }
  }

  public void close() {
    if (active.compareAndSet(true, false)) {
      try {
        selectionKey.cancel();
        socketChannel.close();
      } catch (IOException e) {
        // 무시
      } finally {
        ConnectionManager.decrement();
      }
    }
  }

  public SocketChannel socketChannel() {
    return socketChannel;
  }

  public SelectionKey selectionKey() {
    return selectionKey;
  }
}
