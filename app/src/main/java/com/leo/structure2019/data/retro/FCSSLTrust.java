package com.leo.structure2019.data.retro;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.CertificatePinner;
import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;
import okio.Buffer;
import timber.log.Timber;

public final class FCSSLTrust {
  private OkHttpClient.Builder builder;

  public FCSSLTrust(OkHttpClient.Builder builder, String url) {
    X509TrustManager trustManager;
    SSLSocketFactory sslSocketFactory;
    try {
      trustManager = trustManagerForCertificates(trustedCertificatesInputStream());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] { trustManager }, null);
      sslSocketFactory = sslContext.getSocketFactory();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }

    //
    ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .cipherSuites(CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA)
            .build();


    this.builder = builder;
    this.builder.sslSocketFactory(sslSocketFactory, trustManager);
    // this.builder.connectionSpecs(Collections.singletonList(spec));

    Timber.i("Builder : "+ this.builder+" URL : "+ url);
  }

  public OkHttpClient.Builder getBuilder(){
    return builder;
  }

  /**
   * Returns an input stream containing one or more certificate PEM files. This implementation just
   * embeds the PEM files in Java strings; most applications will instead read this from a resource
   * file that gets bundled with the application.
   */
  private InputStream trustedCertificatesInputStream() {
    // PEM files for root certificates of Comodo and Entrust. These two CAs are sufficient to view
    // https://publicobject.com (Comodo) and https://squareup.com (Entrust). But they aren't
    // sufficient to connect to most HTTPS sites including https://godaddy.com and https://visa.com.
    // Typically developers will need to get a PEM file from their organization's TLS administrator.
    return new Buffer()
            .writeUtf8(FCCertificate.MOBITRAIL_CERTIFICATE)
        .inputStream();
  }

  /**
   * Returns a trust manager that trusts {@code certificates} and none other. HTTPS services whose
   * certificates have not been signed by these certificates will fail with a {@code
   * SSLHandshakeException}.
   *
   * <p>This can be used to replace the host platform's built-in trusted certificates with a custom
   * set. This is useful in development where certificate authority-trusted certificates aren't
   * available. Or in production, to avoid reliance on third-party certificate authorities.
   *
   * <p>See also {@link CertificatePinner}, which can limit trusted certificates while still using
   * the host platform's built-in trust store.
   *
   * <h3>Warning: Customizing Trusted Certificates is Dangerous!</h3>
   *
   * <p>Relying on your own trusted certificates limits your server team's ability to update their
   * TLS certificates. By installing a specific set of trusted certificates, you take on additional
   * operational complexity and limit your ability to migrate between certificate authorities. Do
   * not use custom trusted certificates in production without the blessing of your server's TLS
   * administrator.
   */
  private X509TrustManager trustManagerForCertificates(InputStream in)
      throws GeneralSecurityException {
    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
    if (certificates.isEmpty()) {
      throw new IllegalArgumentException("expected non-empty set of trusted certificates");
    }

    // Put the certificates a key store.
    char[] password = "password".toCharArray(); // Any password will work.
    KeyStore keyStore = newEmptyKeyStore(password);
    int index = 0;
    for (Certificate certificate : certificates) {
      String certificateAlias = Integer.toString(index++);
      keyStore.setCertificateEntry(certificateAlias, certificate);
    }

    // Use it to build an X509 trust manager.
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
        KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, password);
    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
        TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);
    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
    if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
      throw new IllegalStateException("Unexpected default trust managers:"
          + Arrays.toString(trustManagers));
    }
    return (X509TrustManager) trustManagers[0];
  }

  private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      InputStream in = null; // By convention, 'null' creates an empty key store.
      keyStore.load(in, password);
      return keyStore;
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}