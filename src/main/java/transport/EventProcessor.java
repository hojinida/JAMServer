package main.java.transport;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import main.java.channel.Channel;
import main.java.util.ConnectionManager;
import main.java.util.buffer.BufferPool;

public class EventProcessor implements Closeable {
  private final Queue<SocketChannel>[] pendingChannels;
  private final Selector[] selectors;
  private final ExecutorService executor;
  private final int size;
  private final ChannelInitializer channelInitializer;

  @SuppressWarnings("unchecked")
  public EventProcessor(int size, ChannelInitializer channelInitializer) throws IOException {
    this.size = size;
    this.selectors = new Selector[size];
    this.executor = Executors.newFixedThreadPool(size);
    this.pendingChannels = new ConcurrentLinkedQueue[size];
    this.channelInitializer = channelInitializer;

    for (int i = 0; i < size; i++) {
      this.selectors[i] = Selector.open();
      this.pendingChannels[i] = new ConcurrentLinkedQueue<>();
    }
  }

  public void start() {
    for (int i = 0; i < size; i++) {
      final int index = i;
      executor.execute(() -> run(index));
    }
  }

  private void run(int index) {
    Selector selector = selectors[index];
    try {
      while (!Thread.currentThread().isInterrupted()) {
        selector.select(100);
        registerPendingChannels(index);
        processSelectedKeys(index);
      }
    } catch (IOException e) {
      System.err.println("Error in event processor #" + index + ": " + e.getMessage());
    } catch (Exception e) {
      System.err.println("Unexpected error in event processor #" + index + ": " + e.getMessage());
    }
  }

  public void registerChannel(SocketChannel channel, int processorIndex) {
    if (processorIndex < 0 || processorIndex >= size) {
      throw new IllegalArgumentException("Invalid processor index: " + processorIndex);
    }

    pendingChannels[processorIndex].add(channel);
    selectors[processorIndex].wakeup();
  }

  private void registerPendingChannels(int index) throws IOException {
    Selector selector = selectors[index];
    Queue<SocketChannel> queue = pendingChannels[index];

    SocketChannel socketChannel;
    while ((socketChannel = queue.poll()) != null) {
      try {
        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);

        Channel channel = new Channel(socketChannel, key);

        key.attach(channel);

        channelInitializer.initChannel(channel);

        channel.activate();
      } catch (Exception e) {
        System.err.println("Error registering channel: " + e.getMessage());
        try {
          socketChannel.close();
          ConnectionManager.decrement();
        } catch (IOException closeEx) {
          // 무시
        }
      }
    }
  }

  private void processSelectedKeys(int index) throws IOException {
    Selector selector = selectors[index];
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();

    while (it.hasNext()) {
      SelectionKey key = it.next();
      it.remove();

      try {
        if (!key.isValid()) {
          continue;
        }

        Channel channel = (Channel) key.attachment();

        if (key.isReadable()) {
          processRead(channel);
        }

        if (key.isWritable()) {
          // 미구현
        }
      } catch (Exception e) {
        System.err.println("Error processing key: " + e.getMessage());
        closeChannel(key);
      }
    }
  }

  private void processRead(Channel channel) throws Exception {
    ByteBuffer readBuffer = BufferPool.getInstance().acquire();

    try {
      int bytesRead = channel.read(readBuffer);

      if (bytesRead == -1) {
        closeChannel(channel.selectionKey());
        return;
      }

      if (bytesRead > 0) {
        readBuffer.flip();

        channel.pipeline().fireChannelRead(readBuffer);
        channel.pipeline().fireChannelReadComplete();
      }
    } finally {
      BufferPool.getInstance().release(readBuffer);
    }
  }
  private void closeChannel(SelectionKey key) {
    try {
      Channel channel = (Channel) key.attachment();
      if (channel != null) {
        channel.close();
      } else {
        key.cancel();
        if (key.channel().isOpen()) {
          key.channel().close();
        }
        ConnectionManager.decrement();
      }
    } catch (Exception e) {
      System.err.println("Error closing channel: " + e.getMessage());
    }
  }

  public int size() {
    return size;
  }

  @Override
  public void close(){
    executor.shutdown();

    for (int i = 0; i < size; i++) {
      Selector selector = selectors[i];

      for (SelectionKey key : selector.keys()) {
        closeChannel(key);
      }

      try {
        selector.wakeup();
        selector.close();
      } catch (IOException e) {
        // 무시
      }
    }
  }
}
