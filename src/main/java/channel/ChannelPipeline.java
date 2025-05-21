package main.java.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelPipeline {

  private final Channel channel;

  private ChannelHandlerContext head;
  private ChannelHandlerContext tail;

  private final Map<String, ChannelHandlerContext> name2ctx = new ConcurrentHashMap<>();

  public ChannelPipeline(Channel channel) {
    this.channel = channel;
  }

  public Channel channel() {
    return channel;
  }

  public ChannelPipeline add(String name, ChannelHandler handler) {
    if (name == null) {
      throw new IllegalArgumentException("Name cannot be null");
    }
    if (handler == null) {
      throw new IllegalArgumentException("Handler cannot be null");
    }
    if (name2ctx.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate handler name: " + name);
    }

    ChannelHandlerContext newCtx = new ChannelHandlerContext(this, handler);

    synchronized (this) {
      if (tail == null) {
        head = tail = newCtx;
      } else {
        newCtx.prev = tail;
        tail.next = newCtx;
        tail = newCtx;
      }

      name2ctx.put(name, newCtx);
    }

    return this;
  }

  public ChannelPipeline fireChannelActive() throws Exception {
    if (head != null) {
      head.handler().channelActive(head);
    }
    return this;
  }

  public ChannelPipeline fireChannelInactive() throws Exception {
    if (head != null) {
      head.handler().channelInactive(head);
    }
    return this;
  }

  public ChannelPipeline fireChannelRead(Object msg) throws Exception {
    if (head != null) {
      head.handler().channelRead(head, msg);
    }
    return this;
  }

  public ChannelPipeline fireChannelReadComplete() throws Exception {
    if (head != null) {
      head.handler().channelReadComplete(head);
    }
    return this;
  }

  public ChannelPipeline fireChannelWritable() throws Exception {
    if (head != null) {
      head.handler().channelWritable(head);
    }
    return this;
  }

  public ChannelPipeline fireExceptionCaught(Throwable cause) throws Exception {
    if (head != null) {
      head.handler().exceptionCaught(head, cause);
    }
    return this;
  }
}
