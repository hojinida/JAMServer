package main.java.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskQueue {
  private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

  public void add(Runnable task) {
    if (task != null) {
      tasks.offer(task);
    }
  }

  public Runnable poll() {
    return tasks.poll();
  }

  public boolean isEmpty() {
    return tasks.isEmpty();
  }

  public int executeTasks(int maxTasks) {
    int executed = 0;
    Runnable task;

    while (executed < maxTasks && (task = poll()) != null) {
      try {
        task.run();
        executed++;
      } catch (Exception e) {
        System.err.println("Error executing task: " + e.getMessage());
      }
    }

    return executed;
  }
}
