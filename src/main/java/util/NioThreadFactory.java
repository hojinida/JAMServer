package main.java.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


public class NioThreadFactory implements ThreadFactory {

  private static final AtomicInteger poolNumber = new AtomicInteger(1);
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final String namePrefix;
  private final boolean daemon;
  private final int priority;

  public NioThreadFactory(String poolName) {
    this(poolName, true, Thread.NORM_PRIORITY);
  }

  public NioThreadFactory(String poolName, boolean daemon, int priority) {
    if (priority < Thread.MIN_PRIORITY || priority > Thread.MAX_PRIORITY) {
      throw new IllegalArgumentException("Invalid thread priority: " + priority);
    }
    if (poolName == null || poolName.isEmpty()) {
      throw new IllegalArgumentException("Pool name cannot be null or empty");
    }

    this.namePrefix = poolName + "-" + poolNumber.getAndIncrement() + "-thread-";
    this.daemon = daemon;
    this.priority = priority;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread thread = new Thread(r, namePrefix + threadNumber.getAndIncrement());
    thread.setDaemon(daemon);
    thread.setPriority(priority);
    thread.setUncaughtExceptionHandler((t, e) -> System.err.println(
        "Uncaught exception in thread " + t.getName() + ": " + e.getMessage()));
    return thread;
  }
}
