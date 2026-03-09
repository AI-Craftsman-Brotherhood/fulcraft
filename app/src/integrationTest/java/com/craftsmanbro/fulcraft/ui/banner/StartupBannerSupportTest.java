package com.craftsmanbro.fulcraft.ui.banner;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;

class StartupBannerSupportTest {

  @Test
  void resolveApplicationMetadata_prefersConfigValues(@TempDir Path tempDir) throws Exception {
    writeConfig(tempDir, tempDir.resolve("config.json"), "banner-model", "custom-ful", "9.9.9");

    assertThat(StartupBannerSupport.resolveApplicationName(tempDir)).isEqualTo("custom-ful");
    assertThat(StartupBannerSupport.resolveApplicationVersion(tempDir)).isEqualTo("9.9.9");
  }

  @Test
  void resolveApplicationMetadata_fallsBackWhenConfigValuesMissing(@TempDir Path tempDir)
      throws Exception {
    writeConfig(tempDir, tempDir.resolve("config.json"), "banner-model", null, null);

    assertThat(StartupBannerSupport.resolveApplicationName(tempDir))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(StartupBannerSupport.resolveApplicationVersion(tempDir))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
  }

  @Test
  void resolveModelName_returnsUnknownWhenConfiguredModelBlank(@TempDir Path tempDir)
      throws Exception {
    writeConfigWithProvider(
        tempDir, tempDir.resolve("config.json"), "mock", "   ", "custom-ful", "9.9.9");

    assertThat(StartupBannerSupport.resolveModelName(tempDir)).isEqualTo("unknown");
  }

  @Test
  void resolveApplicationMetadata_usesFallbackConfigUnderFulDirectory(@TempDir Path tempDir)
      throws Exception {
    Path fallbackConfig = tempDir.resolve(".ful").resolve("config.json");
    writeConfig(tempDir, fallbackConfig, "fallback-model", "fallback-app", "3.2.1");

    assertThat(StartupBannerSupport.resolveApplicationName(tempDir)).isEqualTo("fallback-app");
    assertThat(StartupBannerSupport.resolveApplicationVersion(tempDir)).isEqualTo("3.2.1");
    assertThat(StartupBannerSupport.resolveModelName(tempDir)).isEqualTo("fallback-model");
  }

  @Test
  void resolveApplicationMetadata_fallsBackToDefaultsWhenConfigIsInvalid(@TempDir Path tempDir)
      throws Exception {
    Files.writeString(tempDir.resolve("config.json"), "{ this is not valid json");

    assertThat(StartupBannerSupport.resolveApplicationName(tempDir))
        .isEqualTo(StartupBannerSupport.resolveApplicationName());
    assertThat(StartupBannerSupport.resolveApplicationVersion(tempDir))
        .isEqualTo(StartupBannerSupport.resolveApplicationVersion());
    assertThat(StartupBannerSupport.resolveModelName(tempDir))
        .isEqualTo(Config.createDefault().getLlm().getModelName());
  }

  @Test
  @ResourceLock(value = "user.home", mode = ResourceAccessMode.READ_WRITE)
  void formatPath_shortensHomePath(@TempDir Path tempDir) throws Exception {
    String originalHome = System.getProperty("user.home");
    Path fakeHome = tempDir.resolve("home");
    Files.createDirectories(fakeHome.resolve("workspace").resolve("demo"));
    System.setProperty("user.home", fakeHome.toString());
    try {
      assertThat(StartupBannerSupport.formatPath(fakeHome)).isEqualTo("~");
      assertThat(StartupBannerSupport.formatPath(fakeHome.resolve("workspace").resolve("demo")))
          .isEqualTo("~/workspace/demo");
    } finally {
      restoreUserHome(originalHome);
    }
  }

  @Test
  @ResourceLock(value = "user.home", mode = ResourceAccessMode.READ_WRITE)
  void formatPath_keepsAbsolutePathWhenOutsideHome(@TempDir Path tempDir) throws Exception {
    String originalHome = System.getProperty("user.home");
    Path fakeHome = tempDir.resolve("home");
    Path outside = tempDir.resolve("outside").resolve("project");
    Files.createDirectories(fakeHome);
    Files.createDirectories(outside);
    System.setProperty("user.home", fakeHome.toString());
    try {
      String expected = outside.toAbsolutePath().normalize().toString().replace('\\', '/');
      assertThat(StartupBannerSupport.formatPath(outside)).isEqualTo(expected);
    } finally {
      restoreUserHome(originalHome);
    }
  }

  private static void writeConfig(
      Path projectRoot, Path configPath, String modelName, String appName, String version)
      throws Exception {
    writeConfigWithProvider(projectRoot, configPath, "openai", modelName, appName, version);
  }

  private static void writeConfigWithProvider(
      Path projectRoot,
      Path configPath,
      String provider,
      String modelName,
      String appName,
      String version)
      throws Exception {
    String normalizedRoot = projectRoot.toString().replace('\\', '/');
    String appNameSection = appName == null ? "" : "  \"AppName\": \"" + appName + "\",\n";
    String versionSection = version == null ? "" : "  \"version\": \"" + version + "\",\n";
    String apiKeySection = "openai".equals(provider) ? "    \"api_key\": \"dummy\",\n" : "";
    String json =
        """
        {
%s%s  "execution": { "per_task_isolation": false },
          "llm": {
            "provider": "%s",
%s
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
            .formatted(
                appNameSection, versionSection, provider, apiKeySection, modelName, normalizedRoot);
    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, json);
  }

  private static void restoreUserHome(String originalHome) {
    if (originalHome == null) {
      System.clearProperty("user.home");
    } else {
      System.setProperty("user.home", originalHome);
    }
  }
}
