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

    System.out.println("EventProcessor #" + index + " started");

    try {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          if (taskQueue.isEmpty()) {
            selector.select(100);
          } else {
            selector.selectNow();
          }

          registerPendingChannels(index);
          processSelectedKeys(index);
          taskQueue.executeTasks(10);

        } catch (IOException e) {
          System.err.println("IOException in event processor #" + index + ": " + e.getMessage());
          e.printStackTrace();
        } catch (Exception e) {
          System.err.println("Unexpected error in event processor #" + index + ": " + e.getMessage());
          e.printStackTrace();
        }
      }
    } finally {
      System.out.println("EventProcessor #" + index + " terminated");
    }
  }

  public void registerChannel(SocketChannel channel, int processorIndex) {
    if (processorIndex < 0 || processorIndex >= size) {
      throw new IllegalArgumentException("Invalid processor index: " + processorIndex);
    }

    pendingChannels[processorIndex].add(channel);
    selectors[processorIndex].wakeup();
    System.out.println("Channel queued for registration on processor #" + processorIndex);
  }

  private void registerPendingChannels(int index) throws IOException {
    Selector selector = selectors[index];
    Queue<SocketChannel> queue = pendingChannels[index];

    SocketChannel socketChannel;
    while ((socketChannel = queue.poll()) != null) {
      Channel channel = null;

      try {
        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
        channel = channelInitializer.createChannel(socketChannel, key);

        key.attach(channel);
        channel.activate();

      } catch (Exception e) {
        System.err.println("Error registering channel: " + e.getMessage());
        e.printStackTrace();

        if (channel != null) {
          try {
            channel.close();
          } catch (Exception closeEx) {
            System.err.println("Error closing partially created channel: " + closeEx.getMessage());
          }
        } else {
          try {
            socketChannel.close();
            ConnectionManager.decrement();
          } catch (IOException closeEx) {
            System.err.println("Error closing failed socket: " + closeEx.getMessage());
          }
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
          System.out.println("Invalid key detected, skipping");
          continue;
        }

        Channel channel = (Channel) key.attachment();
        if (channel == null) {
          System.err.println("Channel is null for key: " + key);
          closeChannel(key);
          continue;
        }

        if (key.isReadable()) {
          try {
            processRead(channel);
          } catch (Exception e) {
            e.printStackTrace();
            closeChannel(key);
          }
        }

        if (key.isValid() && key.isWritable()) {
          try {
            processWrite(channel);
          } catch (Exception e) {
            e.printStackTrace();
            closeChannel(key);
          }
        }

      } catch (Exception e) {
        System.err.println("Error processing key: " + e.getMessage());
        e.printStackTrace();
        closeChannel(key);
      }
    }
  }

  private void processWrite(Channel channel) throws Exception {
    if (channel != null) {
      channel.handleWrite();
    } else {
      System.err.println("Cannot process write: channel is null");
    }
  }

  private void processRead(Channel channel) throws Exception {
    if (channel != null) {
      channel.handleRead();
    } else {
      System.err.println("Cannot process read: channel is null");
    }
  }

  private void closeChannel(SelectionKey key) {
    try {
      Channel channel = (Channel) key.attachment();
      if (channel != null) {
        System.out.println("Closing channel: " + channel);
        channel.close();
      } else {
        System.out.println("Closing orphaned key");
        key.cancel();
        if (key.channel().isOpen()) {
          key.channel().close();
        }
        ConnectionManager.decrement();
      }
    } catch (Exception e) {
      System.err.println("Error closing channel: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Override
  public void close() {
    System.out.println("Shutting down EventProcessor...");
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
        System.err.println("Error closing selector #" + i + ": " + e.getMessage());
      }
    }

    System.out.println("EventProcessor shutdown completed");
  }
}
