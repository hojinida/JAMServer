package main.java.handler.codec;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import main.java.channel.Channel;
import main.java.channel.ChannelHandler;
import main.java.channel.ChannelHandlerContext;
import main.java.message.Message;
import main.java.message.MessageEncoder;
import main.java.util.buffer.BufferPool;

public class MessageToByteEncoder extends ChannelHandler {

  private final ConcurrentLinkedQueue<WriteRequest> pendingWrites = new ConcurrentLinkedQueue<>();

  private final AtomicBoolean writeInProgress = new AtomicBoolean(false);

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    ctx.fireChannelRead(msg);
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof Message) {
      Message message = (Message) msg;
      ByteBuffer buffer = MessageEncoder.encode(message);

      Channel channel = ctx.pipeline().channel();
      pendingWrites.offer(new WriteRequest(buffer));

      tryWrite(channel);
    } else {
      ctx.write(msg);
    }
  }

  private void tryWrite(Channel channel) throws Exception {
    if (!writeInProgress.compareAndSet(false, true)) {
      return;
    }

    try {
      SocketChannel socketChannel = channel.socketChannel();

      WriteRequest request;
      while ((request = pendingWrites.peek()) != null) {
        ByteBuffer buffer = request.buffer;

        int written = socketChannel.write(buffer);

        if (buffer.hasRemaining()) {
          if (!channel.selectionKey().isValid()) {
            return;
          }

          int currentOps = channel.selectionKey().interestOps();
          if ((currentOps & java.nio.channels.SelectionKey.OP_WRITE) == 0) {
            channel.selectionKey().interestOps(currentOps | java.nio.channels.SelectionKey.OP_WRITE);
          }

          break;
        } else {
          pendingWrites.poll();

          BufferPool.getInstance().release(buffer);
        }
      }

      if (pendingWrites.isEmpty() && channel.selectionKey().isValid()) {
        int currentOps = channel.selectionKey().interestOps();
        if ((currentOps & java.nio.channels.SelectionKey.OP_WRITE) != 0) {
          channel.selectionKey().interestOps(currentOps & ~java.nio.channels.SelectionKey.OP_WRITE);
        }
      }
    } finally {
      writeInProgress.set(false);

      if (!pendingWrites.isEmpty()) {
        tryWrite(channel);
      }
    }
  }

  private static class WriteRequest {
    final ByteBuffer buffer;

    WriteRequest(ByteBuffer buffer) {
      this.buffer = buffer;
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    clearPendingWrites();
    ctx.fireChannelInactive();
  }

  private void clearPendingWrites() {
    WriteRequest request;
    while ((request = pendingWrites.poll()) != null) {
      BufferPool.getInstance().release(request.buffer);
    }
  }

  @Override
  public void channelWritable(ChannelHandlerContext ctx) throws Exception {
    tryWrite(ctx.pipeline().channel());

    ctx.fireChannelWritable();
  }
}
