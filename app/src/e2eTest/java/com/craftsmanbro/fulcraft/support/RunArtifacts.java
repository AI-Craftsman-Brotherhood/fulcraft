package com.craftsmanbro.fulcraft.support;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Locates and reads the artifacts of the most recent run under a runs root, so end-to-end tests can
 * make content assertions on analysis shards and reports rather than just checking exit codes.
 */
public final class RunArtifacts {

  private static final ObjectMapper MAPPER = JsonMapperFactory.create();

  private final Path runRoot;

  private RunArtifacts(final Path runRoot) {
    this.runRoot = runRoot;
  }

  /** Locate the newest {@code <runId>} directory under {@code runsRoot}. */
  public static RunArtifacts locateLatest(final Path runsRoot) throws IOException {
    if (!Files.isDirectory(runsRoot)) {
      throw new AssertionError("runs root does not exist: " + runsRoot);
    }
    try (Stream<Path> children = Files.list(runsRoot)) {
      final Path latest =
          children
              .filter(Files::isDirectory)
              .max(Comparator.comparing(RunArtifacts::lastModified))
              .orElseThrow(() -> new AssertionError("no run directory under " + runsRoot));
      return new RunArtifacts(latest);
    }
  }

  public Path runRoot() {
    return runRoot;
  }

  public Path analysisDir() {
    return runRoot.resolve("analysis");
  }

  public Path reportDir() {
    return runRoot.resolve("report");
  }

  public Path docsDir() {
    return runRoot.resolve("docs");
  }

  /** Parsed {@code type_resolution_summary.json} (engine {@code per_source} counts, etc.). */
  public JsonNode typeResolutionSummary() throws IOException {
    final Path file = analysisDir().resolve("type_resolution_summary.json");
    if (!Files.isRegularFile(file)) {
      throw new AssertionError("type_resolution_summary.json not found under " + analysisDir());
    }
    return MAPPER.readTree(Files.readString(file));
  }

  /** All {@code analysis_*.json} shard files (recursively). */
  public List<Path> analysisShards() throws IOException {
    final Path dir = analysisDir();
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(dir)) {
      return files
          .filter(Files::isRegularFile)
          .filter(
              p -> {
                final String name = p.getFileName().toString();
                return name.startsWith("analysis_") && name.endsWith(".json");
              })
          .sorted()
          .toList();
    }
  }

  /** Fully qualified names of every class across all analysis shards. */
  public List<String> classFqns() throws IOException {
    final List<String> fqns = new ArrayList<>();
    forEachClass((fqn, cls) -> fqns.add(fqn));
    return fqns;
  }

  /** Method names per class FQN (in shard order). */
  public Map<String, List<String>> methodNamesByClass() throws IOException {
    final Map<String, List<String>> result = new LinkedHashMap<>();
    forEachClass(
        (fqn, cls) -> {
          final List<String> names = new ArrayList<>();
          for (final JsonNode method : cls.path("methods")) {
            names.add(method.path("name").asString(""));
          }
          result.put(fqn, names);
        });
    return result;
  }

  /** {@code file_path} value per class FQN. */
  public Map<String, String> filePathByClass() throws IOException {
    final Map<String, String> result = new LinkedHashMap<>();
    forEachClass((fqn, cls) -> result.put(fqn, cls.path("file_path").asString("")));
    return result;
  }

  private interface ClassConsumer {
    void accept(String fqn, JsonNode classNode);
  }

  private void forEachClass(final ClassConsumer consumer) throws IOException {
    for (final Path shard : analysisShards()) {
      final JsonNode root = MAPPER.readTree(Files.readString(shard));
      for (final JsonNode cls : root.path("classes")) {
        final String fqn = cls.path("fqn").asString("");
        if (!fqn.isEmpty()) {
          consumer.accept(fqn, cls);
        }
      }
    }
  }

  private static long lastModified(final Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
