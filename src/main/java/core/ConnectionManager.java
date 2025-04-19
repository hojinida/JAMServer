package main.java.core;

import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionManager {
  private static final AtomicInteger CURRENT = new AtomicInteger(0);
  private static final int MAX = 10_000;

  private ConnectionManager() { /* 인스턴스화 방지 */ }

  public static boolean tryIncrement() {
    for (;;) {
      int cur = CURRENT.get();
      if (cur >= MAX) {
        return false;
      }
      if (CURRENT.compareAndSet(cur, cur + 1)) {
        return true;
      }
    }
  }

  public static void decrement() {
    int v = CURRENT.decrementAndGet();
    if (v < 0) {
      CURRENT.set(0);
    }
  }

  public static int getCurrent() {
    return CURRENT.get();
  }
}
