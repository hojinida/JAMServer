package main.java.handler.business;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import main.java.channel.ChannelHandler;
import main.java.channel.ChannelHandlerContext;
import main.java.message.Message;
import main.java.message.MessageFactory;
import main.java.message.MessageType;
import main.java.util.business.BusinessExecutor;

public class HashBusinessHandler extends ChannelHandler {

  private static final MessageDigest SHA256_DIGEST;

  private static final int MAX_ITERATIONS = 100;
  private static final int MAX_DATA_LENGTH = 128;

  private final BusinessExecutor businessExecutor;

  static {
    try {
      SHA256_DIGEST = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  public HashBusinessHandler(BusinessExecutor businessExecutor) {
    if (businessExecutor == null) {
      throw new IllegalArgumentException("BusinessExecutor cannot be null");
    }
    this.businessExecutor = businessExecutor;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof Message) {
      Message message = (Message) msg;

      if (message.getType() == MessageType.HASH_REQUEST) {
        processHashRequest(ctx, message);
      } else {
        ctx.fireChannelRead(msg);
      }
    } else {
      ctx.fireChannelRead(msg);
    }
  }

  private void processHashRequest(ChannelHandlerContext ctx, Message requestMsg) throws Exception {
    ByteBuffer payload = requestMsg.getPayload();

    // 페이로드 크기 확인
    if (payload.remaining() < 16) { // 최소: requestId(8) + iterations(4) + dataLength(4)
      closeChannelSafely(ctx);
      return;
    }

    // 요청 페이로드에서 파라미터 추출
    long requestId = payload.getLong();
    int iterations = payload.getInt();
    int dataLength = payload.getInt();

    // 입력 데이터 유효성 검사
    if (!validateRequest(requestId, iterations, dataLength, payload.remaining())) {
      closeChannelSafely(ctx);
      return;
    }

    // 해시할 데이터 추출
    byte[] data = new byte[dataLength];
    payload.get(data);

    // 비즈니스 로직을 별도 스레드 풀에서 실행
    try {
      businessExecutor.submit(() -> executeHashCalculation(ctx, requestId, iterations, data));
    } catch (IllegalStateException e) {
      closeChannelSafely(ctx);
    }
  }

  private boolean validateRequest(long requestId, int iterations, int dataLength, int remainingBytes) {
    if (requestId <= 0) {
      return false;
    }

    if (iterations < 1 || iterations > MAX_ITERATIONS) {
      return false;
    }

    if (dataLength < 0 || dataLength > MAX_DATA_LENGTH) {
      return false;
    }

    if (dataLength > remainingBytes) {
      return false;
    }

    return true;
  }

  private void executeHashCalculation(ChannelHandlerContext ctx, long requestId, int iterations, byte[] data) {
    try {
      byte[] hashResult = calculateHash(data, iterations);

      Message responseMsg = MessageFactory.createHashResponse(requestId, iterations, hashResult);

      ctx.write(responseMsg);

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      closeChannelSafely(ctx);

    } catch (Exception e) {
      closeChannelSafely(ctx);
    }
  }

  private byte[] calculateHash(byte[] data, int iterations) {
    byte[] result = data.clone();

    for (int i = 0; i < iterations; i++) {
      synchronized (SHA256_DIGEST) {
        SHA256_DIGEST.reset();
        result = SHA256_DIGEST.digest(result);
      }
    }

    return result;
  }

  private void closeChannelSafely(ChannelHandlerContext ctx) {
    try {
      ctx.pipeline().channel().close();
    } catch (Exception closeEx) {
      // 무시
    }
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.fireChannelInactive();
  }

  public BusinessExecutor getBusinessExecutor() {
    return businessExecutor;
  }
}
