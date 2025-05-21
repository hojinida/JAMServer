package main.java.channel;

public class ChannelHandler {

  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelActive();
  }

  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelInactive();
  }

  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ctx.fireChannelRead(msg);
  }

  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelReadComplete();
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    ctx.fireExceptionCaught(cause);
  }

  public void write(ChannelHandlerContext ctx, Object msg) throws Exception {
    ctx.write(msg);
  }

  public void channelWritable(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelWritable();
  }
}
