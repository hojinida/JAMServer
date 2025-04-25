package main.java.channel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChannelPipeline {
  private final Channel channel;

  // 첫번째와 마지막 컨텍스트
  private ChannelHandlerContext head;
  private ChannelHandlerContext tail;

  // 이름으로 컨텍스트 조회를 위한 맵
  private final Map<String, ChannelHandlerContext> name2ctx = new ConcurrentHashMap<>();

  // 대기 중인 쓰기 작업을 위한 큐
  private final Queue<ByteBuffer> pendingWrites = new ConcurrentLinkedQueue<>();
  private final AtomicBoolean writePending = new AtomicBoolean(false);

  public ChannelPipeline(Channel channel) {
    this.channel = channel;
  }

  /**
   * 이 파이프라인이 연결된 채널 반환
   */
  public Channel channel() {
    return channel;
  }

  /**
   * 파이프라인 마지막에 핸들러 추가
   */
  public ChannelPipeline addLast(String name, ChannelHandler handler) {
    if (name == null) {
      throw new IllegalArgumentException("Name cannot be null");
    }
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }
    if (name2ctx.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate handler name: " + name);
    }

    ChannelHandlerContext newCtx = new ChannelHandlerContext(this, name, handler);

    synchronized (this) {
      if (tail == null) {
        // 첫 번째 핸들러
        head = tail = newCtx;
      } else {
        // 기존 tail의 다음에 추가
        newCtx.prev = tail;
        tail.next = newCtx;
        tail = newCtx;
      }

      name2ctx.put(name, newCtx);
    }

    return this;
  }

  /**
   * 파이프라인 첫번째에 핸들러 추가
   */
  public ChannelPipeline addFirst(String name, ChannelHandler handler) {
    if (name == null || handler == null || name2ctx.containsKey(name)) {
      throw new IllegalArgumentException("Invalid handler name or duplicate");
    }

    ChannelHandlerContext newCtx = new ChannelHandlerContext(this, name, handler);

    synchronized (this) {
      if (head == null) {
        // 첫 번째 핸들러
        head = tail = newCtx;
      } else {
        // 기존 head의 이전에 추가
        newCtx.next = head;
        head.prev = newCtx;
        head = newCtx;
      }

      name2ctx.put(name, newCtx);
    }

    return this;
  }

  /**
   * 지정된 이름의 핸들러 제거
   */
  public ChannelPipeline remove(String name) {
    ChannelHandlerContext ctx = name2ctx.remove(name);
    if (ctx != null) {
      synchronized (this) {
        // 이전/다음 컨텍스트 링크 갱신
        if (ctx.prev != null) {
          ctx.prev.next = ctx.next;
        } else {
          head = ctx.next;
        }

        if (ctx.next != null) {
          ctx.next.prev = ctx.prev;
        } else {
          tail = ctx.prev;
        }
      }
    }

    return this;
  }

  /**
   * 지정된 핸들러 제거
   */
  public ChannelPipeline remove(ChannelHandler handler) {
    if (handler == null) {
      return this;
    }

    for (Map.Entry<String, ChannelHandlerContext> entry : name2ctx.entrySet()) {
      if (entry.getValue().handler() == handler) {
        remove(entry.getKey());
        break;
      }
    }

    return this;
  }

  /**
   * 지정된 이름의 핸들러 반환
   */
  public ChannelHandler get(String name) {
    ChannelHandlerContext ctx = name2ctx.get(name);
    return ctx != null ? ctx.handler() : null;
  }

  /**
   * 채널 활성화 이벤트 발생
   */
  public ChannelPipeline fireChannelActive() throws Exception {
    if (head != null) {
      head.handler().channelActive(head);
    }
    return this;
  }

  /**
   * 채널 비활성화 이벤트 발생
   */
  public ChannelPipeline fireChannelInactive() throws Exception {
    if (head != null) {
      head.handler().channelInactive(head);
    }
    return this;
  }

  /**
   * 채널 읽기 이벤트 발생
   */
  public ChannelPipeline fireChannelRead(Object msg) throws Exception {
    if (head != null) {
      head.handler().channelRead(head, msg);
    }
    return this;
  }

  /**
   * 채널 읽기 완료 이벤트 발생
   */
  public ChannelPipeline fireChannelReadComplete() throws Exception {
    if (head != null) {
      head.handler().channelReadComplete(head);
    }
    return this;
  }

  /**
   * 예외 발생 이벤트 발생
   */
  public ChannelPipeline fireExceptionCaught(Throwable cause) throws Exception {
    if (head != null) {
      head.handler().exceptionCaught(head, cause);
    }
    return this;
  }

  /**
   * 데이터 쓰기 요청
   */
  public ChannelPipeline write(Object msg) throws Exception {
    if (tail != null) {
      tail.handler().write(tail, msg);
    }
    return this;
  }

  /**
   * 쓰기 버퍼 플러시 요청
   */
  public ChannelPipeline flush() throws Exception {
    if (tail != null) {
      tail.handler().flush(tail);
    }
    return this;
  }

  /**
   * 쓰기 후 즉시 플러시 요청
   */
  public ChannelPipeline writeAndFlush(Object msg) throws Exception {
    write(msg);
    flush();
    return this;
  }

  /**
   * 실제 채널에 쓰기 작업 수행 (파이프라인 내부용)
   */
  void writeToChannel(Object msg) throws Exception {
    if (!(msg instanceof ByteBuffer)) {
      throw new IllegalArgumentException("Only ByteBuffer can be written to channel");
    }

    ByteBuffer buffer = (ByteBuffer) msg;

    // 쓰기 큐에 추가
    pendingWrites.offer(buffer);

    // 쓰기 관심사 등록이 필요하면 등록
    if (writePending.compareAndSet(false, true)) {
      channel.setWriteInterest(true);
    }
  }

  /**
   * 채널 플러시 수행 (현재는 아무 작업 없음, 모든 쓰기는 즉시 처리)
   */
  void flushChannel() {
    // NOP - 현재 구현에서는 writeToChannel에서 즉시 쓰기 관심사 등록
  }

  /**
   * 대기 중인 쓰기 작업 처리
   */
  public boolean flushPendingWrites() throws IOException {
    ByteBuffer buffer;
    boolean hasRemaining = false;

    // 모든 대기 중인 버퍼를 처리 시도
    while ((buffer = pendingWrites.peek()) != null) {
      try {
        boolean complete = channel.write(buffer);

        if (!complete) {
          // 아직 더 쓸 내용이 있음
          hasRemaining = true;
          break;
        } else {
          // 이 버퍼는 완전히 쓰여짐, 제거하고 다음으로
          pendingWrites.poll();
        }
      } catch (IOException e) {
        // 쓰기 실패 시 예외 전파
        pendingWrites.clear(); // 모든 대기 작업 취소
        writePending.set(false);
        throw e;
      }
    }

    // 모든 버퍼를 처리했고 추가 쓰기가 없으면 쓰기 관심사 제거
    if (!hasRemaining && pendingWrites.isEmpty()) {
      writePending.set(false);
      channel.setWriteInterest(false);
      return false; // 더 이상 쓸 데이터 없음
    }

    return true; // 아직 쓸 데이터가 있음
  }
}
