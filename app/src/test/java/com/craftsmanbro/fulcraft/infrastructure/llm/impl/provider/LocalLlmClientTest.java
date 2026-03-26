package com.craftsmanbro.fulcraft.infrastructure.llm.impl.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderException;
import com.craftsmanbro.fulcraft.infrastructure.llm.exception.LlmProviderHttpException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LocalLlmClientTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void generateTest_sendsChatCompletionRequest_andParsesUsage() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(200)
              .addHeader("Content-Type", "application/json")
              .setBody(
                  """
                      {
                        "choices": [
                          {
                            "message": {
                              "content": "```java\\nclass LocalTest {}\\n```"
                            }
                          }
                        ],
                        "usage": {
                          "prompt_tokens": 12,
                          "completion_tokens": 34,
                          "total_tokens": 46
                        }
                      }
                      """));
      server.start();

      Config.LlmConfig config = new Config.LlmConfig();
      config.setProvider("local");
      config.setUrl(server.url("/v1").toString());
      config.setModelName("local-model");
      config.setConnectTimeout(2);
      config.setRequestTimeout(5);
      config.setMaxRetries(0);
      config.setRetryInitialDelayMs(1L);

      LocalLlmClient client = new LocalLlmClient(config);
      String result = client.generateTest("Hello local", config);

      assertThat(result).isEqualTo("class LocalTest {}");

      var recorded = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(recorded).isNotNull();
      assertThat(recorded.getPath()).isEqualTo("/v1/chat/completions");
      JsonNode body = mapper.readTree(recorded.getBody().readUtf8());
      assertThat(body.path("model").asText()).isEqualTo("local-model");
      assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("user");
      assertThat(body.path("messages").path(0).path("content").asText()).isEqualTo("Hello local");
      assertThat(body.path("temperature").asDouble()).isEqualTo(0.0);

      assertThat(client.getLastUsage()).isPresent();
      var usage = client.getLastUsage().orElseThrow();
      assertThat(usage.getPromptTokens()).isEqualTo(12);
      assertThat(usage.getCompletionTokens()).isEqualTo(34);
      assertThat(usage.getTotalTokens()).isEqualTo(46);
    }
  }

  @Test
  void generateTest_throwsProviderHttpException_onHttpError() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
      server.start();

      Config.LlmConfig config = new Config.LlmConfig();
      config.setProvider("local");
      config.setUrl(server.url("/v1").toString());
      config.setModelName("local-model");
      config.setMaxRetries(0);
      config.setRetryInitialDelayMs(1L);

      LocalLlmClient client = new LocalLlmClient(config);

      assertThatThrownBy(() -> client.generateTest("Hello local", config))
          .isInstanceOf(LlmProviderHttpException.class)
          .hasMessageContaining("Local LLM HTTP error");
    }
  }

  @Test
  void generateTest_throwsProviderException_onConnectionFailure() throws Exception {
    String baseUrl;
    try (MockWebServer server = new MockWebServer()) {
      server.start();
      baseUrl = server.url("/v1").toString();
      // Server is closed here, simulating connection failure for subsequent calls
    }

    Config.LlmConfig config = new Config.LlmConfig();
    config.setProvider("local");
    config.setUrl(baseUrl);
    config.setModelName("local-model");
    config.setConnectTimeout(1);
    config.setRequestTimeout(1);
    config.setMaxRetries(0);
    config.setRetryInitialDelayMs(1L);

    LocalLlmClient client = new LocalLlmClient(config);

    assertThatThrownBy(() -> client.generateTest("Hello local", config))
        .isInstanceOf(LlmProviderException.class)
        .hasMessageContaining("Failed to generate test with local LLM");
  }

  @Test
  void isHealthy_usesModelsEndpoint_whenConfigUsesChatCompletionsUrl() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setResponseCode(200));
      server.start();

      Config.LlmConfig config = new Config.LlmConfig();
      config.setProvider("local");
      config.setUrl(server.url("/v1/chat/completions").toString());
      config.setModelName("local-model");

      LocalLlmClient client = new LocalLlmClient(config);

      assertThat(client.isHealthy()).isTrue();
      var recorded = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(recorded).isNotNull();
      assertThat(recorded.getPath()).isEqualTo("/v1/models");
    }
  }
}
