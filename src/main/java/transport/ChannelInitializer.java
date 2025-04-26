package main.java.transport;


import main.java.channel.Channel;
import main.java.handler.codec.ByteToMessageDecoder;
import main.java.handler.codec.MessageToByteEncoder;

public class ChannelInitializer {

  public void initChannel(Channel channel) throws Exception {
    channel.pipeline().add("decoder", new ByteToMessageDecoder())
        .add("encoder", new MessageToByteEncoder());

    // 비즈니스 로직 핸들러는 필요에 따라 추가
    // channel.pipeline().addLast("business", new BusinessHandler());
  }
}

