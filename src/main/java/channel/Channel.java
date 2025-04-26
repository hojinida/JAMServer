package main.java.channel;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import main.java.core.ConnectionManager;

public class Channel implements Closeable {
  private final SocketChannel socketChannel;
  private final SelectionKey selectionKey;
  private final ChannelPipeline pipeline;
  private volatile boolean active = false;

  public Channel(SocketChannel socketChannel, SelectionKey selectionKey) throws Exception {
    if (socketChannel == null || selectionKey == null) {
      throw new IllegalArgumentException("SocketChannel and SelectionKey cannot be null");
    }

    this.socketChannel = socketChannel;
    this.selectionKey = selectionKey;

    this.pipeline = new ChannelPipeline(this);
  }

  public SocketChannel socketChannel() {
    return socketChannel;
  }

  public SelectionKey selectionKey() {
    return selectionKey;
  }

  public ChannelPipeline pipeline() {
    return pipeline;
  }

  public int read(ByteBuffer buffer) throws IOException {
    return socketChannel.read(buffer);
  }

  public void activate() throws Exception {
    if (!active) {
      active = true;
      pipeline.fireChannelActive();
    }
  }

  public void deactivate() throws Exception {
    if (active) {
      active = false;
      pipeline.fireChannelInactive();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      deactivate();
    } catch (Exception e) {
      System.err.println("Error during channel deactivation: " + e.getMessage());
    }

    selectionKey.cancel();

    socketChannel.close();

    ConnectionManager.decrement();
  }
}
