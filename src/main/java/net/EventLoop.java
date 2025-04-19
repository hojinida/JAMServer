package main.java.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class EventLoop implements Runnable{
  private final Selector selector;

  public EventLoop() throws IOException {
    this.selector = Selector.open();
  }

  public void registerChannel(SocketChannel channel) throws IOException {
    // Wake up selector if it's blocked
    selector.wakeup();
    // Register the channel for read events
    channel.register(selector, SelectionKey.OP_READ);
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        selector.select(); // block until events arrive
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();

          if (!key.isValid()) {
            continue;
          }

          if (key.isReadable()) {
            handleRead(key);
          }
          // TODO: implement OP_WRITE handling when needed
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try { selector.close(); } catch (IOException ignored) {}
    }
  }

  private void handleRead(SelectionKey key) throws IOException {
    SocketChannel ch = (SocketChannel) key.channel();
    ByteBuffer buf = ByteBuffer.allocate(1024);
    int len = ch.read(buf);
    if (len < 0) {
      key.cancel();
      ch.close();
      return;
    }
    buf.flip();
    // TODO: pass buf into your pipeline (SslHandler, FrameDecoder, BusinessLogic, etc.)
    // For now, echo back:
    ch.write(buf);
  }
}
