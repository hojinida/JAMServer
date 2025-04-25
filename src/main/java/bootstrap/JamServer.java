package main.java.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import main.java.transport.AcceptorGroup;
import main.java.transport.WorkerSelector;

public class JamServer implements AutoCloseable {
  private final AcceptorGroup acceptSelector;
  private final WorkerSelector workerSelector;

  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    this.workerSelector = new WorkerSelector(nCores * 2);
    this.workerSelector.start();

    this.acceptSelector = new AcceptorGroup(2, address, workerSelector);
    this.acceptSelector.start();
  }

  @Override
  public void close() throws IOException {
    acceptSelector.close();
    workerSelector.close();
  }

  public static void main(String[] args) throws IOException {
    final int port = 8888;
    final JamServer server = new JamServer(port);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        server.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }));
  }
}
