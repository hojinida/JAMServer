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
  private static final int HASH_RESULT_SIZE = 32; // SHA-256
  private static final int HEADER_SIZE = 6; // 메시지 헤더 크기

  private final BusinessExecutor businessExecutor;

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

    // 비동기 실행
    businessExecutor.submit(() -> executeHashCalculation(channel, requestId, iterations, data));
  }

  private boolean isValidRequest(long requestId, int iterations, int dataLength, int remaining) {
    return requestId > 0
        && iterations >= 1
        && iterations <= MAX_ITERATIONS
        && dataLength >= 0
        && dataLength <= MAX_DATA_LENGTH
        && dataLength <= remaining;
  }

  /**
   * 수정된 해시 계산 - 버퍼를 한 번만 할당하고 직접 인코딩
   */
  private void executeHashCalculation(Channel channel, long requestId, int iterations, byte[] data) {
    ByteBuffer responseBuffer = null;

    try {
      if (!channel.isActive()) {
        return;
      }

      MessageDigest digest = digestPool.get();
      byte[] result = data;

      // 해시 계산
      for (int i = 0; i < iterations; i++) {
        digest.reset();
        result = digest.digest(result);

        if (iterations > 50 && i % 25 == 0 && !channel.isActive()) {
          return;
        }
      }

      if (!channel.isActive()) {
        return;
      }

      responseBuffer = BufferPool.getInstance().acquireResponseBuffer();

      int payloadSize = 8 + 4 + 4 + HASH_RESULT_SIZE;

      responseBuffer.clear();
      responseBuffer.putInt(payloadSize);
      responseBuffer.putShort(MessageType.HASH_RESPONSE.getValue());

      responseBuffer.putLong(requestId);
      responseBuffer.putInt(iterations);
      responseBuffer.putInt(HASH_RESULT_SIZE);
      responseBuffer.put(result, 0, HASH_RESULT_SIZE);
      responseBuffer.flip();

      channel.writeDirectBuffer(responseBuffer);

      responseBuffer = null;

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (responseBuffer != null) {
        BufferPool.getInstance().releaseResponseBuffer(responseBuffer);
      }
    } catch (Exception e) {
      if (responseBuffer != null) {
        BufferPool.getInstance().releaseResponseBuffer(responseBuffer);
      }
    }
  }
}
