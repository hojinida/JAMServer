package main.java.handler.business;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import main.java.channel.Channel;
import main.java.message.Message;
import main.java.message.MessageType;
import main.java.util.buffer.BufferPool;
import main.java.util.business.BusinessExecutor;

public class HashRequestHandler {

  private static final int MAX_ITERATIONS = 100;
  private static final int MAX_DATA_LENGTH = 128;

  private final BusinessExecutor businessExecutor;

  // ThreadLocal로 스레드별 MessageDigest
  private final ThreadLocal<MessageDigest> digestPool = ThreadLocal.withInitial(() -> {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  });

  public HashRequestHandler(BusinessExecutor businessExecutor) {
    this.businessExecutor = businessExecutor;
  }

  public void handle(Message message, Channel channel) {
    if (message.getType() != MessageType.HASH_REQUEST) {
      return;
    }

    ByteBuffer payload = message.getPayload();

    if (payload.remaining() < 16) {
      channel.close();
      return;
    }

    long requestId = payload.getLong();
    int iterations = payload.getInt();
    int dataLength = payload.getInt();

    if (!isValidRequest(requestId, iterations, dataLength, payload.remaining())) {
      channel.close();
      return;
    }

    byte[] data = new byte[dataLength];
    payload.get(data);

    businessExecutor.submit(() -> executeHashCalculation(channel, requestId, iterations, data));
  }

  private boolean isValidRequest(long requestId, int iterations, int dataLength, int remaining) {
    return requestId > 0 && iterations >= 1 && iterations <= MAX_ITERATIONS && dataLength >= 0
        && dataLength <= MAX_DATA_LENGTH && dataLength <= remaining;
  }

  private void executeHashCalculation(Channel channel, long requestId, int iterations, byte[] data) {
    try {
      MessageDigest digest = digestPool.get();
      byte[] result = data;

      for (int i = 0; i < iterations; i++) {
        digest.reset();
        result = digest.digest(result);
      }

      // BufferPool에서 응답용 버퍼 할당
      ByteBuffer responseBuffer = BufferPool.getInstance().acquire();
      responseBuffer.clear();
      responseBuffer.putLong(requestId);
      responseBuffer.putInt(iterations);
      responseBuffer.putInt(32);
      responseBuffer.put(result);
      responseBuffer.flip();

      Message response = new Message(MessageType.HASH_RESPONSE, responseBuffer);
      channel.write(response);

      // 버퍼는 Channel.write()에서 관리됨

    } catch (Exception e) {
      channel.close();
    }
  }
}
