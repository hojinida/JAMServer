package main.java.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import main.java.transport.ChannelInitializer;
import main.java.transport.ConnectionAcceptor;
import main.java.transport.EventProcessor;

public class JamServer implements AutoCloseable {
  private final ConnectionAcceptor connectionAcceptor;
  private final EventProcessor eventProcessor;

  public JamServer(int port) throws IOException {
    int nCores = Runtime.getRuntime().availableProcessors();
    InetSocketAddress address = new InetSocketAddress(port);

    ChannelInitializer initializer = new ChannelInitializer();

    this.eventProcessor = new EventProcessor(nCores * 2, initializer);
    this.eventProcessor.start();

    this.connectionAcceptor = new ConnectionAcceptor(2, address, eventProcessor);
    this.connectionAcceptor.start();

    System.out.println("Server started on port " + port);
    System.out.println("Processors: " + nCores + ", Event threads: " + (nCores * 2));
  }

  @Override
  public void close() throws IOException {
    System.out.println("Shutting down server...");

    if (connectionAcceptor != null) {
      connectionAcceptor.close();
    }

    if (eventProcessor != null) {
      eventProcessor.close();
    }
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
