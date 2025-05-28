package main.java.handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import main.java.util.NioThreadFactory;

public class BusinessExecutor implements AutoCloseable {

  private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;

  private final ExecutorService executorService;
  private final int shutdownTimeoutSeconds;
  private volatile boolean shutdown = false;

  public BusinessExecutor() {
    this(Runtime.getRuntime().availableProcessors());
  }

  public BusinessExecutor(int threadCount) {
    this(threadCount, DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
  }

  public BusinessExecutor(int threadCount, int shutdownTimeoutSeconds) {
    if (threadCount <= 0) {
      throw new IllegalArgumentException("Thread count must be positive: " + threadCount);
    }
    if (shutdownTimeoutSeconds < 0) {
      throw new IllegalArgumentException(
          "Shutdown timeout must be non-negative: " + shutdownTimeoutSeconds);
    }

    this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    this.executorService = Executors.newFixedThreadPool(threadCount, new NioThreadFactory("business-pool"));
  }

  public void submit(Runnable task) {
    if (task == null) {
      throw new IllegalArgumentException("Task cannot be null");
    }
    if (shutdown) {
      throw new IllegalStateException("BusinessExecutor is already shutdown");
    }
    try {
      executorService.submit(task);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to submit task: " + e.getMessage(), e);
    }
  }

  @Override
  public void close() {
    if (shutdown) {
      return;
    }
    shutdown = true;
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
