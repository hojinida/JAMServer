package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageFactory {

  private MessageFactory() {

  }

  public static Message createHashRequest(long requestId, int iterations, byte[] data) throws InterruptedException {
    ByteBuffer buffer = BufferPool.getInstance().acquire();
    try {
      buffer.putLong(requestId);
      buffer.putInt(iterations);
      buffer.putInt(data.length);
      buffer.put(data);
      buffer.flip();

      return new Message(MessageType.HASH_REQUEST, buffer);
    } catch (Exception e) {
      BufferPool.getInstance().release(buffer);
      throw e;
    }
  }

  public static Message createHashResponse(long requestId, int iterations, byte[] hashResult) throws InterruptedException {
    ByteBuffer buffer = BufferPool.getInstance().acquire();
    try {
      buffer.putLong(requestId);
      buffer.putInt(iterations);
      buffer.putInt(hashResult.length);
      buffer.put(hashResult);
      buffer.flip();

      return new Message(MessageType.HASH_RESPONSE, buffer);
    } catch (Exception e) {
      BufferPool.getInstance().release(buffer);
      throw e;
    }
  }
}
