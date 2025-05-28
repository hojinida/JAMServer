package main.java.channel;

import java.nio.ByteBuffer;
import java.util.List;
import main.java.handler.HashRequestHandler;
import main.java.message.Message;
import main.java.message.MessageDecoder;
import main.java.message.MessageDecoder.DecodeException;
import main.java.server.NioChannel;

public class ChannelHandler {

  private final MessageDecoder decoder;
  private final HashRequestHandler businessHandler;

  public ChannelHandler(MessageDecoder decoder, HashRequestHandler businessHandler) {
    this.decoder = decoder;
    this.businessHandler = businessHandler;
  }

  public void channelRead(NioChannel channel, ByteBuffer buffer) {
    try {
      List<Message> messages = decoder.decode(buffer);
      for (Message message : messages) {
        fireMessageReceived(channel, message);
      }
    } catch (DecodeException e) {
      System.err.println("Channel #" + channel.getChannelId() + " decode error: " + e.getMessage());
      channel.close();
    } catch (Exception e) {
      System.err.println(
          "Channel #" + channel.getChannelId() + " processing error: " + e.getMessage());
      e.printStackTrace();
      channel.close();
    }
  }

  protected void fireMessageReceived(NioChannel channel, Message message) {
    businessHandler.handle(message, channel);
  }

  public void exceptionCaught(NioChannel channel, Throwable cause) {
    System.err.println(
        "Exception caught for Channel #" + channel.getChannelId() + ": " + cause.getMessage());
    cause.printStackTrace();
    channel.close();
  }
}
