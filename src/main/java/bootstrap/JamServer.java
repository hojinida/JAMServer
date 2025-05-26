package main.java.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import main.java.transport.ChannelInitializer;
import main.java.transport.ConnectionAcceptor;
import main.java.transport.EventProcessor;
import main.java.util.buffer.BufferPool;
import main.java.util.business.BusinessExecutor;

public class JamServer implements AutoCloseable {

  private final ConnectionAcceptor connectionAcceptor;
  private final EventProcessor eventProcessor;
  private final BusinessExecutor businessExecutor;

  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    BufferPool.getInstance();

    this.businessExecutor = new BusinessExecutor();
    ChannelInitializer initializer = new ChannelInitializer(businessExecutor);

    this.eventProcessor = new EventProcessor(nCores * 2, initializer);
    this.eventProcessor.start();

    this.connectionAcceptor = new ConnectionAcceptor(2, address, eventProcessor);
    this.connectionAcceptor.start();

    System.out.println("서버 가동 port " + port);
    System.out.println("Processors: " + nCores + ", Event threads: " + (nCores * 2));
  }

  @Override
  public void close() throws IOException {
    if (connectionAcceptor != null) {
      connectionAcceptor.close();
    }

    if (eventProcessor != null) {
      eventProcessor.close();
    }

    if (businessExecutor != null) {
      businessExecutor.close();
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    final int port = 8888;
    final JamServer server = new JamServer(port);

    CountDownLatch shutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.close();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        shutdownLatch.countDown();
      }
    }));

    shutdownLatch.await();
  }
}
