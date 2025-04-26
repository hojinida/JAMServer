package main.java.transport;


import main.java.channel.Channel;

public class ChannelInitializer {

  public void initChannel(Channel channel) throws Exception {
    channel.pipeline().addLast("decoder", new main.java.message.ByteToMessageDecoder())
        .addLast("encoder", new main.java.message.MessageToByteEncoder());

    // 비즈니스 로직 핸들러는 필요에 따라 추가
    // channel.pipeline().addLast("business", new BusinessHandler());
  }
}

