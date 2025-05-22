package main.java.transport;


import main.java.channel.Channel;
import main.java.handler.business.HashBusinessHandler;
import main.java.handler.codec.ByteToMessageDecoder;
import main.java.handler.codec.MessageToByteEncoder;

public class ChannelInitializer {

  public void initChannel(Channel channel) throws Exception {
    channel.pipeline().add("decoder", new ByteToMessageDecoder())
        .add("encoder", new MessageToByteEncoder())
        .add("business", new HashBusinessHandler());
  }
}

