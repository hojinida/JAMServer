package main.java.server;

public class ServerConfig {

  public static final int DEFAULT_PORT = 8888;
  public static final long GRACEFUL_SHUTDOWN_TIMEOUT_SECONDS = 10;
  public static final int N_CORES = Runtime.getRuntime().availableProcessors();
  public static final int ACCEPTOR_COUNT = 2;
  public static final int EVENT_LOOP_COUNT = N_CORES * 2;


  // NioAcceptor Config
  public static final int BACKLOG = 1024;
  public static final int RECEIVE_BUFFER_SIZE = 65536;
  public static final int MAX_CONNECTIONS = 30000;

  // NioEventLoop Config
  public static final long SELECT_TIMEOUT = 500; // ms

  // NioChannel Config
  public static final int READ_BUFFER_SIZE = 1024;

  // BusinessExecutor Config
  public static final int BUSINESS_THREAD_COUNT = N_CORES;
  public static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;

  // HashRequestHandler Config
  public static final int MAX_ITERATIONS = 100;
  public static final int MAX_DATA_LENGTH = 128;
  public static final int HASH_RESULT_SIZE = 32;
  public static final int REQUEST_ID_SIZE = 8;
  public static final int ITERATIONS_SIZE = 4;
  public static final int DATA_LENGTH_SIZE = 4;
  public static final int REQUEST_HEADER_SIZE =
      REQUEST_ID_SIZE + ITERATIONS_SIZE + DATA_LENGTH_SIZE;
  public static final int RESPONSE_PAYLOAD_SIZE =
      REQUEST_ID_SIZE + ITERATIONS_SIZE + DATA_LENGTH_SIZE + HASH_RESULT_SIZE;

  // MessageDecoder Config
  public static final int HEADER_SIZE = 6;
  public static final int MAX_PAYLOAD_SIZE = 256;

  // MessageEncoder Config
  public static final int RESPONSE_BUFFER_CAPACITY = 64; // HASH_RESPONSE: 6 + 8 + 4 + 4 + 32 = 54 bytes
}
