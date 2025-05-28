package main.java.channel;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
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
import main.java.message.MessageDecoder.DecodeException;
import main.java.transport.EventProcessor;
import main.java.util.ConnectionManager;
import main.java.util.buffer.BufferPool;

public class Channel implements Closeable {

  private static final AtomicLong CHANNEL_ID_GENERATOR = new AtomicLong(0);

  private final long channelId;
  private final SocketChannel socketChannel;
  private final SelectionKey selectionKey;
  private final EventProcessor eventProcessor;
  private final int processorIndex;

  private final ByteBuffer dedicatedReadBuffer;
  private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean active = new AtomicBoolean(false);

  private final MessageDecoder decoder;
  private final HashRequestHandler businessHandler;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey,
      EventProcessor eventProcessor, int processorIndex, MessageDecoder decoder,
      HashRequestHandler businessHandler) {

    this.channelId = CHANNEL_ID_GENERATOR.incrementAndGet();
    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;
    this.eventProcessor = eventProcessor;
    this.processorIndex = processorIndex;
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


  public void handleRead() {
    if (!isActive()) {
      return;
    }

    int bytesRead;
    try {
      bytesRead = socketChannel.read(dedicatedReadBuffer);
    } catch (IOException e) {
      System.err.println("Channel #" + channelId + " read error: " + e.getMessage());
      internalClose();
      return;
    }

    if (bytesRead == -1) {
      internalClose();
      return;
    }

    if (bytesRead > 0) {
      dedicatedReadBuffer.flip();
      try {
        List<Message> messages = decoder.decode(dedicatedReadBuffer);
        for (Message message : messages) {
          businessHandler.handle(message, this);
        }
      } catch (DecodeException e) {
        System.err.println("Channel #" + channelId + " decode error: " + e.getMessage());
        internalClose();
      } catch (Exception e) {
        System.err.println(
            "Channel #" + channelId + " error processing messages: " + e.getMessage());
        e.printStackTrace();
        internalClose();
      } finally {
        if (dedicatedReadBuffer.hasRemaining()) {
          dedicatedReadBuffer.compact();
        } else {
          dedicatedReadBuffer.clear();
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
      System.err.println(
          "Channel #" + channelId + " write error in handleWrite: " + e.getMessage());
      internalClose();
    }
  }

  private void flush() throws IOException {
    ByteBuffer buffer;
    while ((buffer = writeQueue.peek()) != null) {
      try {
        socketChannel.write(buffer);
      } catch (IOException e) {
        System.err.println("Channel #" + channelId + " write error: " + e.getMessage());
        internalClose();
        throw e;
      }

      if (buffer.hasRemaining()) {
        return;
      }
      writeQueue.poll();
      BufferPool.getInstance().releaseResponseBuffer(buffer);
    }

    if (writeQueue.isEmpty() && selectionKey.isValid()) {
      final int interestOps = selectionKey.interestOps();
      if ((interestOps & SelectionKey.OP_WRITE) != 0) {
        try {
          selectionKey.interestOps(interestOps & ~SelectionKey.OP_WRITE);
        } catch (CancelledKeyException | IllegalArgumentException e) {
          System.err.println(
              "Channel #" + channelId + " error deregistering OP_WRITE: " + e.getMessage());
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
        System.err.println(
            "Channel #" + channelId + " error registering OP_WRITE: " + e.getMessage());
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
      } catch (Exception e) {
        System.err.println("Channel #" + channelId + " error cancelling key: " + e.getMessage());
      }
      try {
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

  public void queueResponse(ByteBuffer buffer) {
    if (!isActive()) {
      BufferPool.getInstance().releaseResponseBuffer(buffer);
      return;
    }

    eventProcessor.addTask(processorIndex, () -> {
      if (isActive()) {
        writeQueue.offer(buffer);
        registerWriteInterestIfNeeded();
      } else {

        BufferPool.getInstance().releaseResponseBuffer(buffer);
      }
    });
    eventProcessor.wakeup(processorIndex);
  }

  public void closeAsync() {
    eventProcessor.addTask(processorIndex, this::internalClose);
    eventProcessor.wakeup(processorIndex);
  }

  @Override
  public void close() {
    closeAsync();
  }
}
