package main.java.core;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import main.java.net.ConnectionAcceptor;
import main.java.net.EventLoop;

public class JamServer {
  public static void main(String[] args) throws Exception {
    final int port = 8888;
    final InetSocketAddress address = new InetSocketAddress(port);

    int nCores     = Runtime.getRuntime().availableProcessors();
    int nEventLoops = nCores * 2;

    ExecutorService workerPool = Executors.newFixedThreadPool(nCores);

    EventLoop[] loops = new EventLoop[nEventLoops];
    for (int i = 0; i < nEventLoops; i++) {
      loops[i] = new EventLoop();
      workerPool.execute(loops[i]);
    }

    ConnectionAcceptor primaryConnectionAcceptor = new ConnectionAcceptor(address, loops);
    ConnectionAcceptor secondaryConnectionAcceptor = new ConnectionAcceptor(address, loops);

    Thread primaryThread = Thread.ofPlatform().name("primary-acceptor")
        .start(primaryConnectionAcceptor);
    Thread secondaryThread = Thread.ofPlatform().name("secondary-acceptor")
        .start(secondaryConnectionAcceptor);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      primaryThread.interrupt();
      secondaryThread.interrupt();
      workerPool.shutdown();
    }));
  }
}
