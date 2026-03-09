package com.craftsmanbro.fulcraft.ui.cli.command.support;

import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Common option accessors and CommonOverrides builder shared by CLI commands. */
public class CommonCliOptionAccessors {

  protected CommonOverrides buildCommonOverrides() {
    return new CommonOverrides()
        .withFiles(getFiles())
        .withDirs(getDirs())
        .withExcludeTests(getExcludeTests().orElse(null))
        .withEnableVersionHistory(getEnableVersionHistory().orElse(null))
        .withDebugDynamicResolution(isDebugDynamicResolution())
        .withExperimentalCandidateEnum(isExperimentalCandidateEnum())
        .withUnresolvedPolicy(getUnresolvedPolicy())
        .withMaxCyclomatic(getMaxCyclomatic())
        .withComplexityStrategy(getComplexityStrategy())
        .withTasksFormat(getTasksFormat())
        .withCacheTtl(getCacheTtl())
        .withCacheRevalidate(getCacheRevalidate().orElse(null))
        .withCacheEncrypt(getCacheEncrypt().orElse(null))
        .withCacheKeyEnv(getCacheKeyEnv())
        .withCacheMaxSizeMb(getCacheMaxSizeMb())
        .withCacheVersionCheck(getCacheVersionCheck().orElse(null))
        .withColorMode(getColorMode())
        .withLogFormat(getLogFormat())
        .withJsonOutput(isJsonOutput());
  }

  protected Path getProjectRootOption() {
    return null;
  }

  protected Path getProjectRootPositional() {
    return null;
  }

  protected boolean isVerboseEnabled() {
    return false;
  }

  protected List<String> getFiles() {
    return List.of();
  }

  protected List<String> getDirs() {
    return List.of();
  }

  protected Optional<Boolean> getExcludeTests() {
    return Optional.empty();
  }

  protected Optional<Boolean> getEnableVersionHistory() {
    return Optional.empty();
  }

  protected boolean isDebugDynamicResolution() {
    return false;
  }

  protected boolean isExperimentalCandidateEnum() {
    return false;
  }

  protected String getUnresolvedPolicy() {
    return null;
  }

  protected Integer getMaxCyclomatic() {
    return null;
  }

  protected String getComplexityStrategy() {
    return null;
  }

  protected String getTasksFormat() {
    return null;
  }

  protected Integer getCacheTtl() {
    return null;
  }

  protected Optional<Boolean> getCacheRevalidate() {
    return Optional.empty();
  }

  protected Optional<Boolean> getCacheEncrypt() {
    return Optional.empty();
  }

  protected String getCacheKeyEnv() {
    return null;
  }

  protected Integer getCacheMaxSizeMb() {
    return null;
  }

  protected Optional<Boolean> getCacheVersionCheck() {
    return Optional.empty();
  }

  protected String getColorMode() {
    return null;
  }

  protected String getLogFormat() {
    return null;
  }

  protected boolean isJsonOutput() {
    return false;
  }
}
