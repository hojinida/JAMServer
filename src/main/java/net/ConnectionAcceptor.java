package main.java.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import main.java.core.ConnectionManager;

public class ConnectionAcceptor implements Runnable {

  private static final int MAX_ACCEPTS_PER_LOOP = 64;
  private static final int BACKLOG = 1024;
  private static final int RECEIVE_BUFFER_SIZE = 65536;

  private final InetSocketAddress listenAddress;
  private final EventLoop[] eventLoops;
  private Selector selector;
  private ServerSocketChannel serverChannel;
  private long acceptCounter = 0;

  public ConnectionAcceptor(InetSocketAddress listenAddress, EventLoop[] eventLoops) {
    this.listenAddress = listenAddress;
    this.eventLoops = eventLoops;
    init();
  }

  private void init() {
    try {
      selector = Selector.open();

      serverChannel = ServerSocketChannel.open();
      serverChannel.setOption(StandardSocketOptions.SO_REUSEPORT, true);
      serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
      serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, RECEIVE_BUFFER_SIZE);
      serverChannel.configureBlocking(false);
      serverChannel.socket().bind(listenAddress, BACKLOG);

      serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    } catch (IOException e) {
      throw new RuntimeException("서버 초기화 중 에러 발생", e);
    }
  }

  @Override
  public void run() {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        selector.select();
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
          SelectionKey key = it.next();
          it.remove();
          if (key.isValid() && key.isAcceptable()) {
            acceptConnection(key);
          }
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      closeSelector();
    }
  }

  private void acceptConnection(SelectionKey key) throws IOException {
    ServerSocketChannel server = (ServerSocketChannel) key.channel();
    for (int i = 0; i < MAX_ACCEPTS_PER_LOOP; i++) {
      SocketChannel client = null;
      try {
        client = server.accept();
        if (client == null) {
          break;
        }

        if (!ConnectionManager.tryIncrement()) {
          client.close();
          continue;
        }

        client.configureBlocking(false);
        client.setOption(StandardSocketOptions.TCP_NODELAY, true);
        client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

        if (acceptCounter == Long.MAX_VALUE) {
          acceptCounter = 0;
        }
        int idx = (int) (++acceptCounter % eventLoops.length);
        eventLoops[idx].registerChannel(client);
      } catch (Exception e) {
        if (client != null) {
          try { client.close(); } catch (IOException ignored) {}
          ConnectionManager.decrement();
        }
        e.printStackTrace();
      }
    }
  }

  private void closeSelector() {
    try {
      if (selector != null) {
        selector.wakeup();
        selector.close();
      }
      if (serverChannel != null) {
        serverChannel.close();
      }
    } catch (IOException ignored) {
    }
  }

}
