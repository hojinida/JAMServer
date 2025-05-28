package main.java.handler.business;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import main.java.channel.Channel;
import main.java.message.Message;
import main.java.message.MessageEncoder;
import main.java.message.MessageType;
import main.java.util.business.BusinessExecutor;

public class HashRequestHandler {

  private static final int MAX_ITERATIONS = 100;
  private static final int MAX_DATA_LENGTH = 128;
  private static final int HASH_RESULT_SIZE = 32; // SHA-256
  private static final int REQUEST_ID_SIZE = 8; // long
  private static final int ITERATIONS_SIZE = 4; // int
  private static final int DATA_LENGTH_SIZE = 4; // int
  private static final int REQUEST_HEADER_SIZE =
      REQUEST_ID_SIZE + ITERATIONS_SIZE + DATA_LENGTH_SIZE;
  private static final int RESPONSE_PAYLOAD_SIZE =
      REQUEST_ID_SIZE + ITERATIONS_SIZE + DATA_LENGTH_SIZE + HASH_RESULT_SIZE;

  private final BusinessExecutor businessExecutor;
  private final MessageEncoder messageEncoder = MessageEncoder.getInstance();
  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST = ThreadLocal.withInitial(() -> {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not available", e);
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
    if (payload.remaining() < REQUEST_HEADER_SIZE) {
      System.err.println("Invalid HASH_REQUEST payload size: " + payload.remaining());
      channel.close();
      return;
    }

    long requestId = payload.getLong();
    int iterations = payload.getInt();
    int dataLength = payload.getInt();

    if (!isValidRequest(requestId, iterations, dataLength, payload.remaining())) {
      System.err.println("Invalid HASH_REQUEST parameters.");
      channel.close();
      return;
    }

    byte[] data = new byte[dataLength];
    payload.get(data);

    businessExecutor.submit(() -> executeHashCalculation(channel, requestId, iterations, data));
  }

  private boolean isValidRequest(long requestId, int iterations, int dataLength, int remaining) {
    return requestId > 0 && iterations >= 1 && iterations <= MAX_ITERATIONS && dataLength >= 0
        && dataLength <= MAX_DATA_LENGTH && dataLength == remaining;
  }

  private void executeHashCalculation(Channel channel, long requestId, int iterations,
      byte[] data) {
    try {
      if (!channel.isActive()) {
        return;
      }

      MessageDigest digest = SHA_256_DIGEST.get();
      byte[] result = data;

      for (int i = 0; i < iterations; i++) {
        if (!channel.isActive()) {
          System.out.println("Channel became inactive during hashing. Aborting.");
          return;
        }
        digest.reset();
        result = digest.digest(result);
      }

      if (!channel.isActive()) {
        return;
      }

      ByteBuffer responsePayloadBuffer = ByteBuffer.allocate(RESPONSE_PAYLOAD_SIZE);
      responsePayloadBuffer.putLong(requestId);
      responsePayloadBuffer.putInt(iterations);
      responsePayloadBuffer.putInt(HASH_RESULT_SIZE);
      responsePayloadBuffer.put(result, 0, HASH_RESULT_SIZE);
      responsePayloadBuffer.flip();

      Message responseMessage = new Message(MessageType.HASH_RESPONSE.getValue(),
          responsePayloadBuffer);

      ByteBuffer encodedResponseBuffer = messageEncoder.encode(responseMessage);

      channel.queueResponse(encodedResponseBuffer);

    } catch (Exception e) {
      System.err.println(
          "Error during hash calculation or response preparation: " + e.getMessage());
      e.printStackTrace();
      channel.closeAsync();
    }
  }
}
