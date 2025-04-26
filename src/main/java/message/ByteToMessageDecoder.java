package main.java.message;

import main.java.channel.ChannelHandler;
import main.java.channel.ChannelHandlerContext;

public class ByteToMessageDecoder extends ChannelHandler {
  protected final MessageParser parser;

  public ByteToMessageDecoder() throws Exception {
    this.parser = new MessageParser();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof java.nio.ByteBuffer) {
      java.nio.ByteBuffer buffer = (java.nio.ByteBuffer) msg;

      // 파서에 데이터 추가
      parser.addData(buffer);

      // 완전한 메시지 추출 및 전달
      Message message;
      while ((message = parser.nextMessage()) != null) {
        ctx.fireChannelRead(message);
      }
    } else {
      // ByteBuffer가 아닌 메시지는 그대로 전달
      ctx.fireChannelRead(msg);
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    try {
      // 리소스 정리
      if (parser != null) {
        parser.close();
      }
    } finally {
      ctx.fireChannelInactive();
    }
  }
}
