package main.java.channel;

import main.java.handler.codec.MessageToByteEncoder;

public class ChannelHandlerContext {
  private final ChannelPipeline pipeline;
  private final ChannelHandler handler;

  ChannelHandlerContext prev;
  ChannelHandlerContext next;

  public ChannelHandlerContext(ChannelPipeline pipeline, ChannelHandler handler) {
    this.pipeline = pipeline;
    this.handler = handler;
  }

  public ChannelHandler handler() {
    return handler;
  }

  public ChannelPipeline pipeline() {
    return pipeline;
  }

  public ChannelHandlerContext fireChannelActive() throws Exception {
    if (next != null) {
      next.handler().channelActive(next);
    }
    return this;
  }

  public ChannelHandlerContext fireChannelInactive() throws Exception {
    if (next != null) {
      next.handler().channelInactive(next);
    }
    return this;
  }

  public ChannelHandlerContext fireChannelRead(Object msg) throws Exception {
    if (next != null) {
      next.handler().channelRead(next, msg);
    }
    return this;
  }

  public ChannelHandlerContext fireChannelReadComplete() throws Exception {
    if (next != null) {
      next.handler().channelReadComplete(next);
    }
    return this;
  }

  public ChannelHandlerContext fireExceptionCaught(Throwable cause) throws Exception {
    if (next != null) {
      next.handler().exceptionCaught(next, cause);
    }
    return this;
  }

  public ChannelHandlerContext write(Object msg) throws Exception {
    if (prev != null) {
      if (prev.handler() instanceof MessageToByteEncoder) {
        (prev.handler()).write(prev, msg);
      } else {
        prev.write(msg);
      }
    }
    return this;
  }

  public ChannelHandlerContext fireChannelWritable() throws Exception {
    if (next != null) {
      next.handler().channelWritable(next);
    }
    return this;
  }
}
