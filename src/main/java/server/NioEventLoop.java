package main.java.server;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import main.java.channel.ChannelHandler;
import main.java.util.NioThreadFactory;

public class NioEventLoop implements Closeable {

  private final Selector selector;
  private final ExecutorService executor;
  private final Queue<SocketChannel> pendingChannels = new ConcurrentLinkedQueue<>();
  private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
  private volatile boolean shutdown = false;
  private final int id;

  private final ChannelHandler channelHandler;
  private final AtomicLong connectionCounter;

  public NioEventLoop(int id, ChannelHandler channelHandler, AtomicLong connectionCounter) throws IOException {
    this.id = id;
    this.selector = Selector.open();
    this.channelHandler = channelHandler;
    this.connectionCounter = connectionCounter;
    this.executor = Executors.newSingleThreadExecutor(new NioThreadFactory("event-loop-" + id));
  }

  public void start() {
    executor.execute(this::run);
  }

  public void addTask(Runnable task) {
    if (task != null && !shutdown) {
      taskQueue.offer(task);
      selector.wakeup();
    }
  }

  private void executeTasks() {
    Runnable task;
    while ((task = taskQueue.poll()) != null) {
      try {
        task.run();
      } catch (Exception e) {
        System.err.println("Error executing task in event loop #" + id + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  private void run() {
    while (!Thread.currentThread().isInterrupted() && !shutdown) {
      try {
        executeTasks();
        registerPendingChannels();

        selector.select(500);

        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          processKey(key);
        }

      } catch (ClosedSelectorException e) {
        break;
      } catch (IOException e) {
        System.err.println("IOException in event loop #" + id + ": " + e.getMessage());
        e.printStackTrace();
      } catch (Exception e) {
        System.err.println("Unexpected error in event loop #" + id + ": " + e.getMessage());
        e.printStackTrace();
      }
    }

    try {
      if (selector.isOpen()) selector.close();
    } catch (IOException e) {
      System.err.println("Error closing selector #" + id + ": " + e.getMessage());
    }
  }

  public void registerChannel(SocketChannel channel) {
    pendingChannels.offer(channel);
    selector.wakeup();
  }

  private void registerPendingChannels() {
    SocketChannel socketChannel;
    while ((socketChannel = pendingChannels.poll()) != null) {
      NioChannel nioChannel = null;
      try {
        socketChannel.configureBlocking(false);
        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ);
        nioChannel = new NioChannel(socketChannel, key, this, channelHandler, connectionCounter);
        key.attach(nioChannel);
      } catch (Exception e) {
        System.err.println("Error registering channel in event loop #" + id + ": " + e.getMessage());
        e.printStackTrace();
        if (nioChannel != null) {
          nioChannel.close();
        } else if (socketChannel != null) {
          try { socketChannel.close(); } catch (IOException ignored) {}
          connectionCounter.decrementAndGet();
        }
      }
    }
  }

  private void processKey(SelectionKey key) {
    NioChannel channel = (NioChannel) key.attachment();

    try {
      if (channel == null || !key.isValid()) {
        closeChannel(key, channel);
        return;
      }

      if (key.isReadable()) {
        channel.handleRead();
      }

      if (key.isValid() && key.isWritable()) {
        channel.handleWrite();
      }

    } catch (CancelledKeyException e) {
      closeChannel(key, channel);
    } catch (Exception e) {
      String channelIdStr = (channel != null) ? String.valueOf(channel.getChannelId()) : "unknown";
      System.err.println("Error processing key for channel #" + channelIdStr + ": " + e.getMessage());
      e.printStackTrace();
      closeChannel(key, channel);
    }
  }


  private void closeChannel(SelectionKey key, NioChannel channel) {
    if (channel != null) {
      channel.closeAsync();
    } else {
      try {
        key.cancel();
        if (key.channel().isOpen()) key.channel().close();
      } catch (Exception e) {
        System.err.println("Error closing orphaned key: " + e.getMessage());
      }
    }
  }

  @Override
  public void close() {
    if (shutdown) return;
    shutdown = true;
    System.out.println("Shutting down EventLoop #" + id + "...");
    executor.shutdown();
    selector.wakeup();

    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
