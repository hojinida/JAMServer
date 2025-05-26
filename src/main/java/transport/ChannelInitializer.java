package main.java.transport;


import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import main.java.channel.Channel;
import main.java.handler.business.HashRequestHandler;
import main.java.message.MessageDecoder;
import main.java.message.MessageEncoder;
import main.java.util.business.BusinessExecutor;

public class ChannelInitializer {

  private final MessageDecoder decoder = MessageDecoder.getInstance();
  private final MessageEncoder encoder = MessageEncoder.getInstance();
  private final HashRequestHandler businessHandler;

  public ChannelInitializer(BusinessExecutor businessExecutor) {
    this.businessHandler = new HashRequestHandler(businessExecutor);
  }

  public Channel createChannel(SocketChannel socketChannel, SelectionKey selectionKey)
      throws InterruptedException {
    return new Channel(socketChannel, selectionKey, decoder, encoder, businessHandler);
  }
}

