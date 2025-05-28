package main.java.util;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionManager {

  private static final Semaphore CONNECTION_SEMAPHORE = new Semaphore(30000);
  private static final AtomicInteger CURRENT = new AtomicInteger(0);
  private static final int MAX = 30000;

  private ConnectionManager() {
  }

  public static boolean tryIncrement() {
    if (CONNECTION_SEMAPHORE.tryAcquire()) {
      CURRENT.incrementAndGet();
      return true;
    }
    System.err.println("Connection rejected: Maximum connections (" + MAX + ") reached");
    return false;
  }

  public static void decrement() {
    CURRENT.decrementAndGet();
    CONNECTION_SEMAPHORE.release();
  }
}
