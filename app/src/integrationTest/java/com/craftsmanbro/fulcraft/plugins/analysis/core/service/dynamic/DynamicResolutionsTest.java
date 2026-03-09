package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.DynamicResolution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicResolutionsTest {

  @TempDir Path tempDir;

  @Test
  void resolve_requiresNonNullInputs() {
    DynamicResolutions resolutions = new DynamicResolutions();

    assertThatThrownBy(() -> resolutions.resolve(null, tempDir, false, 20, false, false))
        .isInstanceOf(NullPointerException.class);

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of());
    assertThatThrownBy(() -> resolutions.resolve(result, null, false, 20, false, false))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resolve_delegatesAndProvidesStats() throws Exception {
    createSourceFile(
        "src/main/java/com/example/Foo.java",
        "package com.example;",
        "class Foo {",
        "  void test() {",
        "    try {",
        "      Class.forName(\"com.example.Bar\");",
        "    } catch (Exception e) {}",
        "  }",
        "}");

    AnalysisResult result = createAnalysisResult("com.example.Foo", "com/example/Foo.java");

    DynamicResolutions resolutions = new DynamicResolutions();
    resolutions.resolve(result, tempDir, false, 20, false, false);

    List<DynamicResolution> items = resolutions.getResolutions();
    assertThat(items).hasSize(1);

    DynamicResolution resolution = items.get(0);
    assertThat(resolution.subtype()).isEqualTo(DynamicResolution.CLASS_FORNAME_LITERAL);
    assertThat(resolution.resolvedClassFqn()).isEqualTo("com.example.Bar");
    assertThat(resolutions.countBySubtype())
        .containsEntry(DynamicResolution.CLASS_FORNAME_LITERAL, 1L);
    assertThat(resolutions.getAverageConfidence()).isEqualTo(resolution.confidence());
    assertThat(resolutions.countByTrustLevel()).containsEntry(resolution.trustLevel().name(), 1L);
    assertThat(resolutions.unwrap()).isNotNull();
  }

  private Path createSourceFile(String relativePath, String... lines) throws Exception {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.write(file, List.of(lines));
    return file;
  }

  private AnalysisResult createAnalysisResult(String fqn, String filePath) {
    ClassInfo info = new ClassInfo();
    info.setFqn(fqn);
    info.setFilePath(filePath);
    info.setMethods(Collections.emptyList());

    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(info));
    return result;
  }
}
