package main.java.net;

import static main.java.message.MessageType.*;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectableChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import main.java.core.ConnectionManager;
import main.java.message.Message;

public class WorkerSelector implements Closeable {
  private final Queue<SocketChannel>[] pendingChannels;
  private final Selector[] selectors;
  private final ExecutorService executor;
  private final int size;

  @SuppressWarnings("unchecked")
  public WorkerSelector(int size) throws IOException {
    this.size = size;
    this.selectors = new Selector[size];
    this.executor = Executors.newFixedThreadPool(size);
    this.pendingChannels = new ConcurrentLinkedQueue[size];

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
        selector.select();
        registerPendingChannels(index);
        processSelectedKeys(index);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void register(SelectableChannel channel,int idx) {
    if (channel instanceof SocketChannel) {
      pendingChannels[idx].add((SocketChannel) channel);
      selectors[idx].wakeup();
    }
  }

  private void registerPendingChannels(int index) throws IOException {
    Selector selector = selectors[index];
    Queue<SocketChannel> queue = pendingChannels[index];

    SocketChannel ch;
    while ((ch = queue.poll()) != null) {
      ch.register(selector, SelectionKey.OP_READ);
    }
  }

  private void processSelectedKeys(int index) throws IOException {
    Selector selector = selectors[index];
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
      SelectionKey key = it.next();
      it.remove();

      if (key.isValid()) {
        if (key.isReadable()) {
          Session session = (Session) key.attachment();

          // 데이터 읽기
          int bytesRead = session.read();

          if (bytesRead == -1) {
            // 연결 종료
            session.close();
            continue;
          }

          // 메시지 처리
          Message message;
          while ((message = session.nextMessage()) != null) {
            processMessage(session, message);
          }
        }
        if (key.isWritable()) {
          // 쓰기 작업 처리
        }
      }
    }
  }

  private void processMessage(Session session, Message message) {
    switch (message.getType()) {
      case BUY_ORDER  :
        //processBuyOrder(session, message);
        break;
      case SELL_ORDER :
        //processSellOrder(session, message);
        break;
      default:
        System.err.println("Unknown message type: " + message.getType());
        break;
    }
  }

  public int size() {
    return size;
  }

  @Override
  public void close() throws IOException {
    executor.shutdown();

    for (int i = 0; i < size; i++) {
      Selector selector = selectors[i];

      for (SelectionKey key : selector.keys()) {
        try {
          ConnectionManager.decrement();

          key.channel().close();
        } catch (IOException e) {
            // Ignore
        }
        key.cancel();
      }

      try {
        selector.wakeup();
        selector.close();
      } catch (IOException e) {
        // Ignore
      }
    }
  }
}
