package main.java.message;

import java.nio.ByteBuffer;
import main.java.util.buffer.BufferPool;

public class MessageParser implements AutoCloseable {
  private ByteBuffer buffer;

  public MessageParser() throws InterruptedException {
    this.buffer = BufferPool.getInstance().acquire();
  }

  public void addData(ByteBuffer data) {
    if (buffer.remaining() < data.remaining()) {
      throw new IllegalStateException("버퍼 공간이 부족합니다. 현재: " + buffer.remaining() +
          ", 필요: " + data.remaining());
    }

    buffer.put(data);
  }

  public Message nextMessage() {
    buffer.flip();

    Message message = MessageDecoder.decode(buffer);

    if (message != null) {
      buffer.compact();

    } else {
      buffer.position(buffer.limit());
      buffer.limit(buffer.capacity());
    }

    return message;
  }

  @Override
  public void close() throws InterruptedException {
    if (buffer != null) {
      BufferPool.getInstance().release(buffer);
      buffer = null;
    }
  }
}
