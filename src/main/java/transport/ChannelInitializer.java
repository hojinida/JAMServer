package main.java.transport;


import main.java.channel.Channel;
import main.java.handler.business.HashBusinessHandler;
import main.java.handler.codec.ByteToMessageDecoder;
import main.java.handler.codec.MessageToByteEncoder;
import main.java.util.business.BusinessExecutor;

public class ChannelInitializer {

  private final BusinessExecutor businessExecutor;

  public ChannelInitializer(BusinessExecutor businessExecutor) {
    if (businessExecutor == null) {
      throw new IllegalArgumentException("BusinessExecutor cannot be null");
    }
    this.businessExecutor = businessExecutor;
  }

  public void initChannel(Channel channel) throws Exception {
    channel.pipeline().add("decoder", new ByteToMessageDecoder())
        .add("encoder", new MessageToByteEncoder())
        .add("business", new HashBusinessHandler(businessExecutor)); // 공유된 BusinessExecutor 사용
  }
}

