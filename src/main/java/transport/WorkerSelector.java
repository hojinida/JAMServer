package main.java.transport;

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
      System.err.println("Error in worker selector #" + index + ": " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      System.err.println("Unexpected error in worker selector #" + index + ": " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void register(SelectableChannel channel, int idx) {
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
      try {
        // 파이프라인 기본 설정
        ch.configureBlocking(false);

        // 채널을 셀렉터에 등록
        SelectionKey key = ch.register(selector, SelectionKey.OP_READ);

        // 파이프라인 생성 및 핸들러 추가
        DefaultChannelPipeline pipeline = createPipeline(ch, key);

        // 파이프라인을 SelectionKey에 첨부
        key.attach(pipeline);

        // 채널 활성화 이벤트 발생
        pipeline.fireChannelActive();

        System.out.println("Registered new connection with pipeline: " + ch);
      } catch (Exception e) {
        System.err.println("Error registering channel: " + e.getMessage());
        try {
          ch.close();
        } catch (IOException closeEx) {
          // 무시
        }
      }
    }
  }

  /**
   * 새 연결에 대한 파이프라인 생성
   */
  private DefaultChannelPipeline createPipeline(SocketChannel channel, SelectionKey key) throws Exception {
    DefaultChannelPipeline pipeline = new DefaultChannelPipeline(channel, key);

    // 핸들러 추가 (순서 중요!)
    pipeline.addLast("logging", new LoggingHandler("CONNECTION"));
    pipeline.addLast("decoder", new DefaultMessageDecoder());
    pipeline.addLast("encoder", new DefaultMessageEncoder());
    pipeline.addLast("businessLogic", new OrderProcessingHandler());

    return pipeline;
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

        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) key.attachment();

        if (key.isReadable()) {
          processRead(pipeline);
        }

        if (key.isWritable()) {
          processWrite(pipeline);
        }
      } catch (Exception e) {
        System.err.println("Error processing key: " + e.getMessage());
        closeConnection(key);
      }
    }
  }

  /**
   * 읽기 이벤트 처리
   */
  private void processRead(DefaultChannelPipeline pipeline) throws Exception {
    SocketChannel channel = pipeline.channel();
    ByteBuffer readBuffer = BufferPool.getInstance().acquire();

    try {
      int bytesRead = channel.read(readBuffer);

      if (bytesRead == -1) {
        // 연결 종료
        closeConnection(pipeline.selectionKey());
        return;
      }

      if (bytesRead > 0) {
        // 버퍼를 읽기 모드로 전환
        readBuffer.flip();

        // 파이프라인으로 이벤트 전달
        pipeline.fireChannelRead(readBuffer);
        pipeline.fireChannelReadComplete();
      }
    } finally {
      // 임시 읽기 버퍼 반환
      BufferPool.getInstance().release(readBuffer);
    }
  }

  /**
   * 쓰기 이벤트 처리
   */
  private void processWrite(DefaultChannelPipeline pipeline) throws Exception {
    try {
      // 파이프라인에 쓰기 처리 위임
      boolean hasMore = pipeline.flushPendingWrites();

      if (!hasMore) {
        // 더 이상 쓸 데이터가 없으면 쓰기 관심 제거 (flushPendingWrites에서 처리됨)
      }
    } catch (IOException e) {
      // 쓰기 에러 발생 시 연결 종료
      closeConnection(pipeline.selectionKey());
      throw e;
    }
  }

  /**
   * 연결 종료 처리
   */
  private void closeConnection(SelectionKey key) {
    try {
      if (key.attachment() instanceof DefaultChannelPipeline) {
        DefaultChannelPipeline pipeline = (DefaultChannelPipeline) key.attachment();

        // 파이프라인 정리
        pipeline.close();
      }

      // 채널 닫기
      if (key.channel().isOpen()) {
        key.channel().close();
      }

      // 키 취소
      key.cancel();

      // 연결 수 감소
      ConnectionManager.decrement();

      System.out.println("Connection closed");
    } catch (Exception e) {
      System.err.println("Error closing connection: " + e.getMessage());
    }
  }

  private void processMessage(DefaultChannelPipeline pipeline, Message message) {
    // 이전 버전의 메시지 처리 로직은 파이프라인 내부 핸들러로 이동
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
        closeConnection(key);
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
