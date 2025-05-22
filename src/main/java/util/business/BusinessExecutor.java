package main.java.util.business;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BusinessExecutor implements AutoCloseable {
  private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;
  private static final int FORCE_SHUTDOWN_TIMEOUT_SECONDS = 2;

  private final ExecutorService executorService;
  private final BusinessThreadFactory threadFactory;
  private final int threadCount;
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
      throw new IllegalArgumentException("Shutdown timeout must be non-negative: " + shutdownTimeoutSeconds);
    }

    this.threadCount = threadCount;
    this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    this.threadFactory = new BusinessThreadFactory();
    this.executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
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

  public boolean isShutdown() {
    return shutdown || executorService.isShutdown();
  }

  public int getThreadCount() {
    return threadCount;
  }

  @Override
  public void close() {
    if (shutdown) {
      return;
    }

    shutdown = true;
    System.out.println("Shutting down BusinessExecutor...");

    executorService.shutdown();

    try {
      if (!executorService.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
        System.out.println("BusinessExecutor did not terminate gracefully within "
            + shutdownTimeoutSeconds + " seconds, forcing shutdown...");

        executorService.shutdownNow();

        if (!executorService.awaitTermination(FORCE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          System.err.println("BusinessExecutor did not terminate after forced shutdown within "
              + FORCE_SHUTDOWN_TIMEOUT_SECONDS + " seconds");
        } else {
          System.out.println("BusinessExecutor terminated after forced shutdown");
        }
      } else {
        System.out.println("BusinessExecutor shutdown completed gracefully");
      }
    } catch (InterruptedException e) {
      System.err.println("BusinessExecutor shutdown interrupted, forcing immediate shutdown...");
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
