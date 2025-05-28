package main.java.transport;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
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

  private final ConcurrentLinkedQueue<SocketChannel>[] pendingChannels;
  private final Selector[] selectors;
  private final TaskQueue[] taskQueues;
  private final ExecutorService executor;
  private final int size;
  private final ChannelInitializer channelInitializer;
  private volatile boolean shutdown = false;

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

  public void addTask(int index, Runnable task) {
    if (index >= 0 && index < size) {
      taskQueues[index].add(task);
      wakeup(index);
    }
  }

  public void wakeup(int index) {
    if (index >= 0 && index < size) {
      selectors[index].wakeup();
    }
  }

  private void run(int index) {
    Selector selector = selectors[index];
    TaskQueue taskQueue = taskQueues[index];

    while (!Thread.currentThread().isInterrupted() && !shutdown) {
      try {
        taskQueue.executeTasks(Integer.MAX_VALUE);
        registerPendingChannels(index);

        selector.select();

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          processKey(key);
        }


      } catch (ClosedSelectorException e) {
        System.out.println("Event processor #" + index + " selector closed, exiting run loop.");
        break;
      } catch (IOException e) {
        System.err.println("IOException in event processor #" + index + ": " + e.getMessage());
        e.printStackTrace();
      } catch (Exception e) {
        System.err.println("Unexpected error in event processor #" + index + ": " + e.getMessage());
        e.printStackTrace();
      }
    }

    try {
      selector.close();
    } catch (IOException e) {
      System.err.println("Error closing selector #" + index + " on termination: " + e.getMessage());
    }
  }

  public void registerChannel(SocketChannel channel, int processorIndex) {
    if (processorIndex < 0 || processorIndex >= size) {
      throw new IllegalArgumentException("Invalid processor index: " + processorIndex);
    }
    pendingChannels[processorIndex].add(channel);
    wakeup(processorIndex);
  }

  private void registerPendingChannels(int index) {
    Selector selector = selectors[index];
    Queue<SocketChannel> queue = pendingChannels[index];

    SocketChannel socketChannel;
    while ((socketChannel = queue.poll()) != null) {
      Channel channel = null;
      try {
        socketChannel.configureBlocking(false);
        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
        channel = channelInitializer.createChannel(socketChannel, key, this, index);
        key.attach(channel);
        channel.activate();

      } catch (Exception e) {
        System.err.println("Error registering channel: " + e.getMessage());
        e.printStackTrace();

        if (channel != null) {
          try {
            channel.close();
          } catch (Exception ignored) {
          }
        } else if (socketChannel != null) {
          try {
            socketChannel.close();
          } catch (IOException ignored) {
          }
          ConnectionManager.decrement();
        }
      }
    }
  }

  private void processKey(SelectionKey key) {
    try {
      if (!key.isValid()) {
        closeChannel(key);
        return;
      }

      Channel channel = (Channel) key.attachment();
      if (channel == null) {
        closeChannel(key);
        return;
      }

      if (key.isReadable()) {
        channel.handleRead();
      }

      if (key.isValid() && key.isWritable()) {
        channel.handleWrite();
      }

    } catch (CancelledKeyException e) {
      System.err.println("Key was cancelled: " + e.getMessage());
      closeChannel(key);
    } catch (Exception e) {
      System.err.println("Error processing key: " + e.getMessage());
      e.printStackTrace();
      closeChannel(key);
    }
  }


  private void closeChannel(SelectionKey key) {
    Object attachment = key.attachment();
    if (attachment instanceof Channel) {
      ((Channel) attachment).closeAsync();
    } else {
      try {
        key.cancel();
        if (key.channel().isOpen()) {
          key.channel().close();
        }
        ConnectionManager.decrement();
      } catch (Exception e) {
        System.err.println("Error closing orphaned key: " + e.getMessage());
      }
    }
  }

  @Override
  public void close() {
    if (shutdown) {
      return;
    }
    shutdown = true;
    System.out.println("Shutting down EventProcessor...");
    executor.shutdown();

    for (Selector selector : selectors) {
      selector.wakeup();
    }

    for (int i = 0; i < size; i++) {
      Selector selector = selectors[i];
      try {
        if (selector.isOpen()) {
          for (SelectionKey key : selector.keys()) {
            closeChannel(key);
          }
          selector.close();
        }
      } catch (IOException e) {
        System.err.println("Error closing selector #" + i + ": " + e.getMessage());
      } catch (Exception e) {
        System.err.println("Unexpected error closing selector #" + i + ": " + e.getMessage());
      }
    }

    System.out.println("EventProcessor shutdown completed");
  }
}
