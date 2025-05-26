package main.java.transport;

import java.io.Closeable;
import java.io.IOException;
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
import main.java.util.TaskQueue;

public class EventProcessor implements Closeable {
  private final ConcurrentLinkedQueue[] pendingChannels;
  private final Selector[] selectors;
  private final TaskQueue[] taskQueues;
  private final ExecutorService executor;
  private final int size;
  private final ChannelInitializer channelInitializer;

  public EventProcessor(int size, ChannelInitializer channelInitializer) throws IOException {
    this.size = size;
    this.selectors = new Selector[size];
    this.executor = Executors.newFixedThreadPool(size);
    this.pendingChannels = new ConcurrentLinkedQueue[size];
    this.taskQueues = new TaskQueue[size];
    this.channelInitializer = channelInitializer;

    for (int i = 0; i < size; i++) {
      this.selectors[i] = Selector.open();
      this.taskQueues[i] = new TaskQueue();
      this.pendingChannels[i] = new ConcurrentLinkedQueue<>();
    }
  }

  public void start() {
    for (int i = 0; i < size; i++) {
      final int index = i;
      executor.execute(() -> run(index));
    }
  }

  public int size() {
    return size;
  }

  private void run(int index) {
    Selector selector = selectors[index];
    TaskQueue taskQueue = taskQueues[index];
    try {
      while (!Thread.currentThread().isInterrupted()) {
        if (taskQueue.isEmpty()) {
          selector.select(100);
        } else {
          selector.selectNow();
        }

        registerPendingChannels(index);
        processSelectedKeys(index);

        taskQueue.executeTasks(10);
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

        Channel channel = channelInitializer.createChannel(socketChannel, key);
        System.out.println("채널 생성");
        key.attach(channel);

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
          System.out.println("Processing read for channel: " + channel);
          processRead(channel);
        }

        if (key.isWritable()) {
          System.out.println("Processing write for channel: " + channel);
          processWrite(channel);
        }
      } catch (Exception e) {
        System.err.println("Error processing key: " + e.getMessage());
        closeChannel(key);
      }
    }
  }

  public void execute(int processorIndex, Runnable task) {
    if (processorIndex < 0 || processorIndex >= size) {
      throw new IllegalArgumentException("Invalid processor index: " + processorIndex);
    }

    taskQueues[processorIndex].add(task);
    selectors[processorIndex].wakeup();
  }

  private void processWrite(Channel channel) throws Exception {
    channel.handleWrite();
  }

  private void processRead(Channel channel) throws Exception {
    channel.handleRead();
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
