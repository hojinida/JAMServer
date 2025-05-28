package main.java.server;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import main.java.channel.ChannelHandler;

public class NioChannel implements Closeable {

  private static final AtomicLong CHANNEL_ID_GENERATOR = new AtomicLong(0);
  private static final int READ_BUFFER_SIZE = 1024;

  private final long channelId;
  private final SocketChannel socketChannel;
  private final SelectionKey selectionKey;
  private final NioEventLoop eventLoop;
  private final AtomicBoolean active = new AtomicBoolean(false);
  private final AtomicLong connectionCounter;

  private final ByteBuffer readBuffer;
  private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();

  private final ChannelHandler handler;

  public NioChannel(SocketChannel socketChannel, SelectionKey selectionKey, NioEventLoop eventLoop,
      ChannelHandler handler, AtomicLong connectionCounter) {

    this.channelId = CHANNEL_ID_GENERATOR.incrementAndGet();
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.eventLoop = eventLoop;
    this.handler = handler;
    this.connectionCounter = connectionCounter;
    this.readBuffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
    this.activate();
  }

  public void activate() {
    if (active.compareAndSet(false, true)) {
    }
  }

  public boolean isActive() {
    return active.get() && socketChannel.isOpen() && selectionKey.isValid();
  }

  public void handleRead() {
    if (!isActive()) {
      return;
    }

    int bytesRead;
    try {
      bytesRead = socketChannel.read(readBuffer);
    } catch (IOException e) {
      handler.exceptionCaught(this, e);
      internalClose();
      return;
    }

    if (bytesRead == -1) {
      internalClose();
      return;
    }

    if (bytesRead > 0) {
      readBuffer.flip();
      try {
        handler.channelRead(this, readBuffer);
      } finally {
        if (readBuffer.hasRemaining()) {
          readBuffer.compact();
        } else {
          readBuffer.clear();
        }
      }
    }
  }

  public void handleWrite() {
    if (!isActive()) {
      return;
    }
    try {
      flush();
    } catch (IOException e) {
      handler.exceptionCaught(this, e);
      internalClose();
    }
  }

  private void flush() throws IOException {
    ByteBuffer buffer;
    while ((buffer = writeQueue.peek()) != null) {
      socketChannel.write(buffer);

      if (buffer.hasRemaining()) {
        return;
      }
      writeQueue.poll();
    }

    if (writeQueue.isEmpty() && selectionKey.isValid()) {
      final int interestOps = selectionKey.interestOps();
      if ((interestOps & SelectionKey.OP_WRITE) != 0) {
        try {
          selectionKey.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        } catch (CancelledKeyException | IllegalArgumentException e) {

        }
      }
    }
  }

  private void registerWriteInterestIfNeeded() {
    if (!isActive() || !selectionKey.isValid()) {
      return;
    }

    final int interestOps = selectionKey.interestOps();
    if ((interestOps & SelectionKey.OP_WRITE) == 0) {
      try {
        selectionKey.interestOps(interestOps | SelectionKey.OP_WRITE);
      } catch (CancelledKeyException | IllegalArgumentException e) {
        internalClose();
      }
    }
  }

  private void internalClose() {
    if (active.compareAndSet(true, false)) {
      try {
        if (selectionKey.isValid()) {
          selectionKey.cancel();
        }
      } catch (Exception e) { /* Ignore */ }
      try {
        socketChannel.close();
      } catch (IOException e) { /* Ignore */ } finally {
        writeQueue.clear();
        connectionCounter.decrementAndGet();
      }
    }
  }

  public void queueResponse(ByteBuffer buffer) {
    if (!isActive()) {
      return;
    }
    eventLoop.addTask(() -> {
      if (isActive()) {
        writeQueue.offer(buffer);
        registerWriteInterestIfNeeded();
      }
    });
  }

  public void closeAsync() {
    eventLoop.addTask(this::internalClose);
  }

  @Override
  public void close() {
    closeAsync();
  }

  public long getChannelId() {
    return channelId;
  }
}
