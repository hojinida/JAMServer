package main.java.util;

public class BufferTimeoutException extends RuntimeException {
  public BufferTimeoutException(String message) {
    super(message);
  }

  public BufferTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
