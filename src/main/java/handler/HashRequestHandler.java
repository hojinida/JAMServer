package main.java.handler;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import main.java.message.Message;
import main.java.message.MessageEncoder;
import main.java.message.MessageType;
import main.java.server.NioChannel;
import main.java.server.ServerConfig;

public class HashRequestHandler {

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

  public void handle(Message message, NioChannel channel) {
    if (message.getType() != MessageType.HASH_REQUEST) {
      System.err.println("Unexpected message type: " + message.getType());
      return;
    }

    try {
      ByteBuffer payload = message.getPayload();
      if (payload.remaining() < ServerConfig.REQUEST_HEADER_SIZE) {
        System.err.println("Invalid HASH_REQUEST payload size: " + payload.remaining());
        channel.close();
        return;
      }

      long requestId = payload.getLong();
      int iterations = payload.getInt();
      int dataLength = payload.getInt();

      if (!isValidRequest(requestId, iterations, dataLength, payload.remaining())) {
        System.err.println("Invalid HASH_REQUEST parameters");
        channel.close();
        return;
      }

      byte[] data = new byte[dataLength];
      payload.get(data);

      businessExecutor.submit(() -> executeHashCalculation(channel, requestId, iterations, data));

    } catch (Exception e) {
      System.err.println("Error handling HASH_REQUEST: " + e.getMessage());
      e.printStackTrace();
      channel.close();
    }
  }

  private boolean isValidRequest(long requestId, int iterations, int dataLength, int remaining) {
    return requestId >= 0 && iterations >= 1 && iterations <= ServerConfig.MAX_ITERATIONS
        && dataLength >= 0 && dataLength <= ServerConfig.MAX_DATA_LENGTH && dataLength == remaining;
  }

  private void executeHashCalculation(NioChannel channel, long requestId, int iterations,
      byte[] data) {
    try {
      if (!channel.isActive()) {
        return;
      }

      MessageDigest digest = SHA_256_DIGEST.get();
      byte[] result = data;

      for (int i = 0; i < iterations; i++) {
        if (!channel.isActive()) {
          return;
        }
        digest.reset();
        result = digest.digest(result);
      }

      if (!channel.isActive()) {
        return;
      }

      ByteBuffer responsePayload = createResponsePayload(requestId, iterations, result);
      Message responseMessage = new Message(MessageType.HASH_RESPONSE.getValue(), responsePayload);
      ByteBuffer encodedResponse = messageEncoder.encode(responseMessage);
      channel.queueResponse(encodedResponse);

    } catch (Exception e) {
      System.err.println(
          "Error during hash calculation for request " + requestId + ": " + e.getMessage());
      e.printStackTrace();
      channel.closeAsync();
    }
  }

  private ByteBuffer createResponsePayload(long requestId, int iterations, byte[] hashResult) {
    if (hashResult.length != ServerConfig.HASH_RESULT_SIZE) {
      throw new IllegalArgumentException("Invalid hash result size: " + hashResult.length);
    }
    ByteBuffer responsePayload = ByteBuffer.allocate(ServerConfig.RESPONSE_PAYLOAD_SIZE);
    responsePayload.putLong(requestId);
    responsePayload.putInt(iterations);
    responsePayload.putInt(ServerConfig.HASH_RESULT_SIZE);
    responsePayload.put(hashResult, 0, ServerConfig.HASH_RESULT_SIZE);
    responsePayload.flip();
    return responsePayload;
  }
}
