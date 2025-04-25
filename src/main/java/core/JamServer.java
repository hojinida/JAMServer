package main.java.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import main.java.net.AcceptSelector;
import main.java.net.WorkerSelector;

public class JamServer implements AutoCloseable {
  private final AcceptSelector acceptSelector;
  private final WorkerSelector workerSelector;

  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    this.workerSelector = new WorkerSelector(nCores * 2);
    this.workerSelector.start();

    this.acceptSelector = new AcceptSelector(2, address, workerSelector);
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
