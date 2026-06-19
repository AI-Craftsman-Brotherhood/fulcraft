package com.craftsmanbro.fulcraft.support;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * A deterministic OpenAI-compatible mock LLM server for end-to-end tests. The dispatcher uses an
 * allowlist: {@code /v1/models} health probes return {@code 200}, {@code /v1/chat/completions}
 * returns a fixed OpenAI-style envelope, and any other path returns {@code 404} so unexpected
 * requests (e.g. a URL-construction regression) fail loudly instead of being masked.
 */
public final class MockLlmServer implements AutoCloseable {

  private final MockWebServer server;
  private final AtomicInteger completionRequests = new AtomicInteger();

  public MockLlmServer(final String chatContent) throws IOException {
    this.server = new MockWebServer();
    final String chatBody = chatEnvelope(chatContent);
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(final RecordedRequest request) {
            final String path = request.getPath() == null ? "" : request.getPath();
            if (path.endsWith("/models")) {
              return new MockResponse().setResponseCode(200);
            }
            if (path.endsWith("/chat/completions")) {
              completionRequests.incrementAndGet();
              return new MockResponse()
                  .setResponseCode(200)
                  .addHeader("Content-Type", "application/json")
                  .setBody(chatBody);
            }
            return new MockResponse().setResponseCode(404).setBody("unexpected path: " + path);
          }
        });
    server.start();
  }

  /** Base URL including the {@code /v1} suffix, suitable for {@code llm.url}. */
  public String baseUrl() {
    return server.url("/v1").toString();
  }

  /** Number of chat-completion requests received (excludes health probes). */
  public int completionRequestCount() {
    return completionRequests.get();
  }

  @Override
  public void close() throws IOException {
    server.close();
  }

  private static String chatEnvelope(final String content) {
    final Map<String, Object> message = new LinkedHashMap<>();
    message.put("role", "assistant");
    message.put("content", content);
    final Map<String, Object> choice = new LinkedHashMap<>();
    choice.put("index", 0);
    choice.put("message", message);
    choice.put("finish_reason", "stop");
    final Map<String, Object> usage = new LinkedHashMap<>();
    usage.put("prompt_tokens", 1);
    usage.put("completion_tokens", 1);
    usage.put("total_tokens", 2);
    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("id", "mock-completion-1");
    root.put("object", "chat.completion");
    root.put("model", "local-model");
    root.put("choices", List.of(choice));
    root.put("usage", usage);
    return JsonMapperFactory.create().writeValueAsString(root);
  }
}
