package com.craftsmanbro.fulcraft.plugins.analysis.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class AnalysisResultReaderTest {

  @TempDir Path tempDir;

  @Test
  void readFrom_nullDirectory_returnsEmpty() {
    AnalysisResultReader reader = new AnalysisResultReader();

    Optional<AnalysisResult> loaded = reader.readFrom(null);

    assertThat(loaded).isEmpty();
  }

  @Test
  void readFrom_missingDirectory_returnsEmpty() {
    AnalysisResultReader reader = new AnalysisResultReader();

    Optional<AnalysisResult> loaded = reader.readFrom(tempDir.resolve("missing"));

    assertThat(loaded).isEmpty();
  }

  @Test
  void readFrom_aggregateFile_returnsResult() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    AnalysisResult original = createResult("project-1", "commit-abc", "com.example.Foo");

    Path aggregateFile = tempDir.resolve("analysis.json");
    mapper.writeValue(aggregateFile.toFile(), original);

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isPresent();
    assertThat(loaded.orElseThrow().getProjectId()).isEqualTo("project-1");
    assertThat(loaded.orElseThrow().getCommitHash()).isEqualTo("commit-abc");
    assertThat(loaded.orElseThrow().getClasses()).hasSize(1);
    assertThat(loaded.orElseThrow().getClasses().get(0).getFqn()).isEqualTo("com.example.Foo");
  }

  @Test
  void readFrom_shardFiles_mergesClassesAndUsesFirstFileMetadata() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();

    Path shardA = tempDir.resolve("a");
    Path shardB = tempDir.resolve("b");
    Files.createDirectories(shardA);
    Files.createDirectories(shardB);

    AnalysisResult partA = createResult("project-a", "commit-a", "com.example.Alpha");
    AnalysisResult partB = createResult("project-b", "commit-b", "com.example.Beta");

    mapper.writeValue(shardA.resolve("analysis_Alpha.json").toFile(), partA);
    mapper.writeValue(shardB.resolve("analysis_Beta.json").toFile(), partB);

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isPresent();
    AnalysisResult merged = loaded.orElseThrow();
    assertThat(merged.getProjectId()).isEqualTo("project-a");
    assertThat(merged.getCommitHash()).isEqualTo("commit-a");
    assertThat(merged.getClasses())
        .extracting(ClassInfo::getFqn)
        .containsExactlyInAnyOrder("com.example.Alpha", "com.example.Beta");
  }

  @Test
  void readFrom_invalidJson_returnsEmpty() throws Exception {
    Path aggregateFile = tempDir.resolve("analysis.json");
    Files.writeString(aggregateFile, "{invalid");

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isEmpty();
  }

  @Test
  void readFrom_invalidShardJson_returnsEmpty() throws Exception {
    Path shardDir = tempDir.resolve("shards");
    Files.createDirectories(shardDir);
    Files.writeString(shardDir.resolve("analysis_Foo.json"), "{invalid");

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isEmpty();
  }

  @Test
  void readFrom_shardFiles_appliesDynamicResolutionsJsonl() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    AnalysisResult shardResult =
        createResultWithMethod("project-1", "commit-abc", "com.example.Foo", "doWork(String)");
    Path shardDir = tempDir.resolve("shard");
    Files.createDirectories(shardDir);
    mapper.writeValue(shardDir.resolve("analysis_Foo.json").toFile(), shardResult);

    DynamicResolution resolution =
        DynamicResolution.builder()
            .classFqn("com.example.Foo")
            .methodSig("doWork(String)")
            .resolvedClassFqn("com.example.deps.Helper")
            .resolvedMethodSig("assist()")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.9)
            .build();
    Files.writeString(
        tempDir.resolve("dynamic_resolutions.jsonl"),
        mapper.writeValueAsString(resolution) + System.lineSeparator());

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isPresent();
    MethodInfo method = loaded.orElseThrow().getClasses().get(0).getMethods().get(0);
    assertThat(method.getDynamicResolutions()).hasSize(1);
    assertThat(method.getDynamicResolutions().get(0).resolvedClassFqn())
        .isEqualTo("com.example.deps.Helper");
  }

  @Test
  void readFrom_shardFiles_doesNotOverrideExistingMethodDynamicResolutions() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    AnalysisResult shardResult =
        createResultWithMethod("project-1", "commit-abc", "com.example.Foo", "doWork(String)");
    DynamicResolution existingResolution =
        DynamicResolution.builder()
            .classFqn("com.example.Foo")
            .methodSig("doWork(String)")
            .resolvedClassFqn("com.example.existing.Helper")
            .resolvedMethodSig("existingAssist()")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(1.0)
            .build();
    shardResult
        .getClasses()
        .get(0)
        .getMethods()
        .get(0)
        .setDynamicResolutions(List.of(existingResolution));

    Path shardDir = tempDir.resolve("shard");
    Files.createDirectories(shardDir);
    mapper.writeValue(shardDir.resolve("analysis_Foo.json").toFile(), shardResult);

    DynamicResolution otherResolution =
        DynamicResolution.builder()
            .classFqn("com.example.Foo")
            .methodSig("doWork(String)")
            .resolvedClassFqn("com.example.other.Helper")
            .resolvedMethodSig("otherAssist()")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.7)
            .build();
    Files.writeString(
        tempDir.resolve("dynamic_resolutions.jsonl"),
        mapper.writeValueAsString(otherResolution) + System.lineSeparator());

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isPresent();
    MethodInfo method = loaded.orElseThrow().getClasses().get(0).getMethods().get(0);
    assertThat(method.getDynamicResolutions()).hasSize(1);
    assertThat(method.getDynamicResolutions().get(0).resolvedClassFqn())
        .isEqualTo("com.example.existing.Helper");
  }

  @Test
  void readFrom_aggregateFile_ignoresInvalidDynamicResolutionLines() throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    AnalysisResult aggregateResult =
        createResultWithMethod("project-1", "commit-abc", "com.example.Foo", "doWork(String)");
    mapper.writeValue(tempDir.resolve("analysis.json").toFile(), aggregateResult);

    DynamicResolution validResolution =
        DynamicResolution.builder()
            .classFqn("com.example.Foo")
            .methodSig("doWork(String)")
            .resolvedClassFqn("com.example.deps.Helper")
            .resolvedMethodSig("assist()")
            .subtype(DynamicResolution.METHOD_RESOLVE)
            .confidence(0.8)
            .build();

    Files.writeString(
        tempDir.resolve("dynamic_resolutions.jsonl"),
        "{invalid}\n\n" + mapper.writeValueAsString(validResolution) + System.lineSeparator());

    AnalysisResultReader reader = new AnalysisResultReader();
    Optional<AnalysisResult> loaded = reader.readFrom(tempDir);

    assertThat(loaded).isPresent();
    MethodInfo method = loaded.orElseThrow().getClasses().get(0).getMethods().get(0);
    assertThat(method.getDynamicResolutions()).hasSize(1);
    assertThat(method.getDynamicResolutions().get(0).resolvedClassFqn())
        .isEqualTo("com.example.deps.Helper");
  }

  private static AnalysisResult createResult(String projectId, String commitHash, String classFqn) {
    AnalysisResult result = new AnalysisResult(projectId);
    result.setCommitHash(commitHash);
    ClassInfo clazz = new ClassInfo();
    clazz.setFqn(classFqn);
    result.setClasses(List.of(clazz));
    return result;
  }

  private static AnalysisResult createResultWithMethod(
      String projectId, String commitHash, String classFqn, String methodSignature) {
    AnalysisResult result = new AnalysisResult(projectId);
    result.setCommitHash(commitHash);
    MethodInfo method = new MethodInfo();
    method.setName("doWork");
    method.setSignature(methodSignature);
    method.setParameterCount(1);
    ClassInfo clazz = new ClassInfo();
    clazz.setFqn(classFqn);
    clazz.setMethods(List.of(method));
    result.setClasses(List.of(clazz));
    return result;
  }
}
