package main.java.util;

import java.util.concurrent.atomic.AtomicInteger;


public final class ConnectionManager {

  private static final AtomicInteger CURRENT = new AtomicInteger(0);
  private static final int MAX = 30000;
  private static final int SPIN_LIMIT = 100;

  private ConnectionManager() { /* 인스턴스화 방지 */ }

  public static boolean tryIncrement() {
    for (int i = 0; i < SPIN_LIMIT; i++) {
      int cur = CURRENT.get();
      if (cur >= MAX) {
        return false;
      }
      if (CURRENT.compareAndSet(cur, cur + 1)) {
        return true;
      }
    }
    System.err.println("ConnectionManager tryIncrement failed: Spin limit reached.");
    return false;
  }

  public static void decrement() {
    CURRENT.updateAndGet(current -> Math.max(0, current - 1));
  }
}
