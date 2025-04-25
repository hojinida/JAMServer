package main.java.channel;

public class ChannelHandler {

  /**
   * 채널이 활성화되었을 때 호출
   */
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelActive();
  }

  /**
   * 채널이 비활성화되었을 때 호출
   */
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelInactive();
  }

  /**
   * 데이터를 읽었을 때 호출
   */
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ctx.fireChannelRead(msg);
  }

  /**
   * 데이터 읽기가 완료되었을 때 호출
   */
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelReadComplete();
  }

  /**
   * 예외가 발생했을 때 호출
   */
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    ctx.fireExceptionCaught(cause);
  }

  /**
   * 채널에 쓰기 요청 시 호출
   */
  public void write(ChannelHandlerContext ctx, Object msg) throws Exception {
    ctx.write(msg);
  }

  /**
   * 모든 쓰기 작업이 flush 될 때 호출
   */
  public void flush(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }
}
