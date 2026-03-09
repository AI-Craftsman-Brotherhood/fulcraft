package com.craftsmanbro.fulcraft.ui.banner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliStartupBannerTest {

  @Test
  void buildLines_rendersCliBannerWithoutSlashCommandHints(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, "banner-model", "custom-ful", "9.9.9");
    Path configPath = tempDir.resolve("config.json");

    List<String> lines =
        CliStartupBanner.buildLines("custom-ful", "9.9.9", "banner-model", tempDir, configPath);

    assertThat(lines).anyMatch(line -> line.contains(">_ custom-ful (v9.9.9)"));
    assertThat(lines).anyMatch(line -> line.contains("model:     banner-model"));
    assertThat(lines).anyMatch(line -> line.contains("config:    "));
    assertThat(lines)
        .anyMatch(
            line -> line.contains("directory: " + StartupBannerSupport.formatDirectory(tempDir)));
    assertThat(lines).noneMatch(line -> line.contains("/model"));
    assertThat(lines).noneMatch(line -> line.contains("/fork"));
  }

  @Test
  void buildLines_usesFallbackValuesWhenInputsAreBlank(@TempDir Path tempDir) {
    List<String> lines = CliStartupBanner.buildLines(" ", " ", null, tempDir, null);

    String expectedTitle =
        ">_ "
            + StartupBannerSupport.resolveApplicationName()
            + " (v"
            + StartupBannerSupport.resolveApplicationVersion()
            + ")";
    assertThat(lines).hasSize(7);
    assertThat(lines).anyMatch(line -> line.contains(expectedTitle));
    assertThat(lines).anyMatch(line -> line.contains("model:     unknown"));
    assertThat(lines)
        .anyMatch(line -> line.contains("config:    ") && line.contains("config.json"));
    assertThat(lines)
        .anyMatch(
            line -> line.contains("directory: " + StartupBannerSupport.formatDirectory(tempDir)));
  }

  @Test
  void buildLines_clipsLongValuesAndKeepsBoxShape(@TempDir Path tempDir) throws Exception {
    Path deepDirectory = tempDir.resolve("a".repeat(64)).resolve("b".repeat(64));
    Files.createDirectories(deepDirectory);
    Path configPath = deepDirectory.resolve("config.json");
    String veryLongValue = "value-".repeat(40);

    List<String> lines =
        CliStartupBanner.buildLines(
            veryLongValue, veryLongValue, veryLongValue, deepDirectory, configPath);

    assertThat(lines).hasSize(7);
    assertThat(lines.get(0)).startsWith("╭").endsWith("╮");
    assertThat(lines.get(6)).startsWith("╰").endsWith("╯");
    assertThat(lines).allSatisfy(line -> assertThat(line).hasSize(53));
    assertThat(lines.subList(1, 6))
        .allSatisfy(line -> assertThat(line).startsWith("│").endsWith("│"));
    assertThat(lines).noneMatch(line -> line.contains(veryLongValue));
  }

  private static void writeConfig(
      Path projectRoot, String modelName, String appName, String version) throws Exception {
    String normalizedRoot = projectRoot.toString().replace('\\', '/');
    String appNameSection = appName == null ? "" : "  \"AppName\": \"" + appName + "\",\n";
    String versionSection = version == null ? "" : "  \"version\": \"" + version + "\",\n";
    String json =
        """
        {
%s%s  "execution": { "per_task_isolation": false },
          "llm": {
            "provider": "openai",
            "api_key": "dummy",
            "fix_retries": 2,
            "max_retries": 3,
            "model_name": "%s"
          },
          "project": { "id": "test-project", "root": "%s" },
          "selection_rules": {
            "class_min_loc": 10,
            "class_min_method_count": 1,
            "exclude_getters_setters": true,
            "method_max_loc": 2000,
            "method_min_loc": 3
          }
        }
        """
            .formatted(appNameSection, versionSection, modelName, normalizedRoot);
    Files.writeString(projectRoot.resolve("config.json"), json);
  }
}
