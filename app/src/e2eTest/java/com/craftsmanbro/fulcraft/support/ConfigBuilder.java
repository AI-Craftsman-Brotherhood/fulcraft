package com.craftsmanbro.fulcraft.support;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * Builds a schema-valid {@code config.json} for end-to-end CLI tests, with the analysis engine and
 * language level parameterized. Uses Jackson (not string templates) so paths and values are escaped
 * correctly.
 */
public final class ConfigBuilder {

  private String engine;
  private String languageLevel = "JAVA_21";
  private String llmProvider = "openai";
  private String llmUrl = "https://api.openai.com/v1";
  private String llmModel = "gpt-4o";

  public static ConfigBuilder create() {
    return new ConfigBuilder();
  }

  /** Sets {@code analysis.engine}; when left unset the key is omitted (engine defaults apply). */
  public ConfigBuilder engine(final String value) {
    this.engine = value;
    return this;
  }

  public ConfigBuilder languageLevel(final String value) {
    this.languageLevel = value;
    return this;
  }

  /** Point the LLM at a (mock) server, e.g. provider {@code local} and a MockWebServer URL. */
  public ConfigBuilder llm(final String provider, final String url, final String model) {
    this.llmProvider = provider;
    this.llmUrl = url;
    this.llmModel = model;
    return this;
  }

  /**
   * Writes {@code config.json} into {@code projectRoot} and returns its path.
   *
   * @param projectRoot the project root (also written into {@code project.root})
   * @return the path of the written config file
   * @throws IOException if the file cannot be written
   */
  public Path writeTo(final Path projectRoot) throws IOException {
    final Map<String, Object> analysis = new LinkedHashMap<>();
    if (engine != null && !engine.isBlank()) {
      analysis.put("engine", engine);
    }
    analysis.put("source_root_mode", "AUTO");
    analysis.put("source_root_paths", List.of("src/main/java"));
    analysis.put("language_level", languageLevel);
    analysis.put("spoon", Map.of("no_classpath", true));

    final Map<String, Object> project = new LinkedHashMap<>();
    project.put("id", "e2e");
    project.put("root", projectRoot.toString());
    project.put("build_tool", "gradle");
    project.put("include_paths", List.of("src/main/java"));
    project.put("exclude_paths", List.of("src/test", "build"));

    final Map<String, Object> selectionRules = new LinkedHashMap<>();
    selectionRules.put("class_min_loc", 1);
    selectionRules.put("class_min_method_count", 1);
    selectionRules.put("method_min_loc", 1);
    selectionRules.put("method_max_loc", 1000);
    selectionRules.put("max_targets", 200);
    selectionRules.put("max_methods_per_class", 5);

    final Map<String, Object> llm = new LinkedHashMap<>();
    llm.put("provider", llmProvider);
    llm.put("model_name", llmModel);
    llm.put("api_key", "test-api-key-placeholder");
    llm.put("url", llmUrl);
    // Keep tests fast and deterministic against the mock server.
    llm.put("max_retries", 0);
    llm.put("connect_timeout", 5);
    llm.put("request_timeout", 10);

    final Map<String, Object> root = new LinkedHashMap<>();
    root.put("schema_version", 1);
    root.put("AppName", "fulcraft");
    root.put("version", "1.0.0");
    root.put("project", project);
    root.put("analysis", analysis);
    root.put("selection_rules", selectionRules);
    root.put("llm", llm);

    final ObjectMapper mapper = JsonMapperFactory.create();
    final Path configPath = projectRoot.resolve("config.json");
    Files.writeString(configPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    return configPath;
  }
}
