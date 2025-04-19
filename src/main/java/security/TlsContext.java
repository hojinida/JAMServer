package main.java.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class TlsContext {
  private final SSLContext sslContext;

  public TlsContext(String keyStorePath, String keyStorePassword) {
    this.sslContext = createSSLContext(keyStorePath, keyStorePassword);
  }

  private static final String KEYSTORE_TYPE = "JKS";
  private static final String SSL_PROTOCOL = "TLS";

  private SSLContext createSSLContext(final String keyStorePath, final String keyStorePassword) {
    try (FileInputStream fis = new FileInputStream(keyStorePath)) {
      KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
      keyStore.load(fis, keyStorePassword.toCharArray());

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(
          KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, keyStorePassword.toCharArray());

      SSLContext sslContext = SSLContext.getInstance(SSL_PROTOCOL);
      sslContext.init(kmf.getKeyManagers(), null, null);

      return sslContext;
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize SSL context", e);
    }
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

}
