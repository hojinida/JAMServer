package main.java.channel;

public class ChannelHandlerContext {
  private final ChannelPipeline pipeline;
  private final ChannelHandler handler;
  private final String name;

  // 이전/다음 노드에 대한 참조
  ChannelHandlerContext prev;
  ChannelHandlerContext next;

  public ChannelHandlerContext(ChannelPipeline pipeline, String name, ChannelHandler handler) {
    this.pipeline = pipeline;
    this.name = name;
    this.handler = handler;
  }

  /**
   * 이 컨텍스트가 연결된 채널 반환
   */
  public Channel channel() {
    return pipeline.channel();
  }

  /**
   * 이 컨텍스트가 연결된 파이프라인 반환
   */
  public ChannelPipeline pipeline() {
    return pipeline;
  }

  /**
   * 이 컨텍스트의 핸들러 반환
   */
  public ChannelHandler handler() {
    return handler;
  }

  /**
   * 이 컨텍스트의 이름 반환
   */
  public String name() {
    return name;
  }

  /**
   * 채널 활성화 이벤트를 다음 핸들러로 전파
   */
  public ChannelHandlerContext fireChannelActive() throws Exception {
    if (next != null) {
      next.handler().channelActive(next);
    }
    return this;
  }

  /**
   * 채널 비활성화 이벤트를 다음 핸들러로 전파
   */
  public ChannelHandlerContext fireChannelInactive() throws Exception {
    if (next != null) {
      next.handler().channelInactive(next);
    }
    return this;
  }

  /**
   * 채널 읽기 이벤트를 다음 핸들러로 전파
   */
  public ChannelHandlerContext fireChannelRead(Object msg) throws Exception {
    if (next != null) {
      next.handler().channelRead(next, msg);
    }
    return this;
  }

  /**
   * 채널 읽기 완료 이벤트를 다음 핸들러로 전파
   */
  public ChannelHandlerContext fireChannelReadComplete() throws Exception {
    if (next != null) {
      next.handler().channelReadComplete(next);
    }
    return this;
  }

  /**
   * 예외 발생 이벤트를 다음 핸들러로 전파
   */
  public ChannelHandlerContext fireExceptionCaught(Throwable cause) throws Exception {
    if (next != null) {
      next.handler().exceptionCaught(next, cause);
    }
    return this;
  }

  /**
   * 데이터 쓰기 요청을 이전 핸들러로 전파
   */
  public ChannelHandlerContext write(Object msg) throws Exception {
    if (prev != null) {
      prev.handler().write(prev, msg);
    } else {
      // 파이프라인의 시작에 도달: 실제 채널에 쓰기
      pipeline.writeToChannel(msg);
    }
    return this;
  }

  /**
   * 쓰기 버퍼 플러시 요청을 이전 핸들러로 전파
   */
  public ChannelHandlerContext flush() throws Exception {
    if (prev != null) {
      prev.handler().flush(prev);
    } else {
      // 파이프라인의 시작에 도달: 실제 채널 플러시
      pipeline.flushChannel();
    }
    return this;
  }
}
