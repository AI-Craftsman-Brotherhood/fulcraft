package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class CommonCliOptionAccessorsTest {

  static class DefaultAccessors extends CommonCliOptionAccessors {
    Path projectRootOption() {
      return getProjectRootOption();
    }

    Path projectRootPositional() {
      return getProjectRootPositional();
    }

    boolean verboseEnabled() {
      return isVerboseEnabled();
    }
  }

  static class FullAccessors extends CommonCliOptionAccessors {
    @Override
    protected List<String> getFiles() {
      return List.of("src/main/java/Foo.java");
    }

    @Override
    protected List<String> getDirs() {
      return List.of("src/main/java/com/example");
    }

    @Override
    protected Optional<Boolean> getExcludeTests() {
      return Optional.of(false);
    }

    @Override
    protected Optional<Boolean> getEnableVersionHistory() {
      return Optional.of(true);
    }

    @Override
    protected boolean isDebugDynamicResolution() {
      return true;
    }

    @Override
    protected boolean isExperimentalCandidateEnum() {
      return true;
    }

    @Override
    protected String getUnresolvedPolicy() {
      return "strict";
    }

    @Override
    protected Integer getMaxCyclomatic() {
      return 17;
    }

    @Override
    protected String getComplexityStrategy() {
      return "balanced";
    }

    @Override
    protected String getTasksFormat() {
      return "yaml";
    }

    @Override
    protected Integer getCacheTtl() {
      return 30;
    }

    @Override
    protected Optional<Boolean> getCacheRevalidate() {
      return Optional.of(true);
    }

    @Override
    protected Optional<Boolean> getCacheEncrypt() {
      return Optional.of(true);
    }

    @Override
    protected String getCacheKeyEnv() {
      return "FUL_CACHE_KEY";
    }

    @Override
    protected Integer getCacheMaxSizeMb() {
      return 512;
    }

    @Override
    protected Optional<Boolean> getCacheVersionCheck() {
      return Optional.of(true);
    }

    @Override
    protected String getColorMode() {
      return "off";
    }

    @Override
    protected String getLogFormat() {
      return "yaml";
    }

    @Override
    protected boolean isJsonOutput() {
      return true;
    }
  }

  @Test
  void buildCommonOverrides_usesDefaults_whenNoOptionsOverridden() {
    CommonOverrides overrides = new DefaultAccessors().buildCommonOverrides();

    assertThat(overrides.getColorMode()).isNull();
    assertThat(overrides.getEffectiveLogFormat()).isNull();
  }

  @Test
  void defaultAccessorMethods_returnNullOrFalse() {
    DefaultAccessors accessors = new DefaultAccessors();

    assertThat(accessors.projectRootOption()).isNull();
    assertThat(accessors.projectRootPositional()).isNull();
    assertThat(accessors.verboseEnabled()).isFalse();
  }

  @Test
  void buildCommonOverrides_appliesAllSupportedOverridesToConfig() {
    CommonOverrides overrides = new FullAccessors().buildCommonOverrides();
    Config config = Config.createDefault();

    overrides.apply(config);

    assertThat(config.getProject().getIncludePaths())
        .containsExactly("src/main/java/Foo.java", "src/main/java/com/example");
    assertThat(config.getAnalysis().getExcludeTests()).isFalse();
    assertThat(config.getAnalysis().getDebugDynamicResolution()).isTrue();
    assertThat(config.getAnalysis().getExperimentalCandidateEnum()).isTrue();

    assertThat(config.getSelectionRules().getVersionHistory().isEnabled()).isTrue();
    assertThat(config.getSelectionRules().getComplexity().getMaxCyclomatic()).isEqualTo(17);
    assertThat(config.getSelectionRules().getComplexity().getStrategy()).isEqualTo("balanced");

    assertThat(config.getExecution().getUnresolvedPolicy()).isEqualTo("strict");
    assertThat(config.getOutput().getTasksFormat()).isEqualTo("yaml");
    assertThat(config.getLog().getFormat()).isEqualTo("json");

    assertThat(config.getCache().getTtlDays()).isEqualTo(30);
    assertThat(config.getCache().isRevalidate()).isTrue();
    assertThat(config.getCache().isEncrypt()).isTrue();
    assertThat(config.getCache().getEncryptionKeyEnv()).isEqualTo("FUL_CACHE_KEY");
    assertThat(config.getCache().getMaxSizeMb()).isEqualTo(512);
    assertThat(config.getCache().isVersionCheck()).isTrue();

    assertThat(config.getCli().getColor()).isEqualTo("off");
  }
}
