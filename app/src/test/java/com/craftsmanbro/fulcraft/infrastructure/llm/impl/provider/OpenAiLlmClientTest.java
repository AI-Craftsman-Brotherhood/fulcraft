package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiLlmClientTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void generateTest_sendsChatCompletionsRequestAndParsesContent() throws Exception {
    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("openai");
    config.setApiKey("test-key");
    config.setModelName("gpt-test");
    config.setUrl("http://localhost/openai");
    config.setMaxRetries(1);

    String responseJson =
        """
        {"choices":[{"message":{"content":"```java\\nclass Foo {}\\n```"}}]}
        """;
    CapturingHttpClient httpClient =
        new CapturingHttpClient(new SimpleHttpResponse(200, responseJson));

    OpenAiLlmClient client = new OpenAiLlmClient(config, httpClient, MAPPER);

    String result = client.generateTest("prompt-text", config);

    assertEquals("class Foo {}", result);
    JsonNode body = MAPPER.readTree(bodyAsString(httpClient.requests.get(0)));
    assertEquals("gpt-test", body.get("model").asText());
    assertEquals("prompt-text", body.get("messages").get(0).get("content").asText());
  }

  private static String bodyAsString(HttpRequest request) throws Exception {
    HttpRequest.BodyPublisher publisher =
        request
            .bodyPublisher()
            .orElseThrow(() -> new IllegalStateException("Request missing body"));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CountDownLatch latch = new CountDownLatch(1);
    publisher.subscribe(
        new java.util.concurrent.Flow.Subscriber<>() {
          @Override
          public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            baos.write(bytes, 0, bytes.length);
          }

          @Override
          public void onError(Throwable throwable) {
            latch.countDown();
          }

          @Override
          public void onComplete() {
            latch.countDown();
          }
        });
    latch.await(1, java.util.concurrent.TimeUnit.SECONDS);
    return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static class SimpleHttpResponse implements HttpResponse<String> {
    private final int status;
    private final String body;

    SimpleHttpResponse(int status, String body) {
      this.status = status;
      this.body = body;
    }

    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public String body() {
      return body;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
      return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
    }

    @Override
    public URI uri() {
      return URI.create("http://localhost/openai");
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }
  }

  private static class CapturingHttpClient extends HttpClient {
    final List<HttpRequest> requests = new java.util.ArrayList<>();
    private final HttpResponse<String> response;

    CapturingHttpClient(HttpResponse<String> response) {
      this.response = response;
    }

    @Override
    public <T> HttpResponse<T> send(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws IOException, InterruptedException {
      requests.add(request);
      HttpResponse<T> casted = (HttpResponse<T>) response;
      return casted;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      return null;
    }

    @Override
    public SSLParameters sslParameters() {
      return null;
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }
  }
}
