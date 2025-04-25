package main.java.message;

import java.nio.ByteBuffer;
import main.java.channel.ChannelHandler;

public class MessageToByteEncoder extends ChannelHandler {
  @Override
  public void write(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof Message) {
      try {
        ByteBuffer buffer = MessageEncoder.encode((Message) msg);

        ctx.write(buffer);
      } catch (Exception e) {
        ctx.fireExceptionCaught(e);
      }
    } else {
      ctx.write(msg);
    }
  }
}
