package com.craftsmanbro.fulcraft.infrastructure.config.impl;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import java.util.ArrayList;
import java.util.List;

/**
 * Common configuration overrides typically used by CLI commands.
 *
 * <p>Provides a fluent builder API to set various configuration overrides that should take
 * precedence over file-based configuration.
 */
public final class CommonOverrides implements ConfigOverride {

  private List<String> files;

  private List<String> dirs;

  private Boolean excludeTests;

  private Boolean enableVersionHistory;

  private boolean debugDynamicResolution;

  private boolean experimentalCandidateEnum;

  private String unresolvedPolicy;

  private Integer maxCyclomatic;

  private String complexityStrategy;

  private String tasksFormat;

  private Integer cacheTtl;

  private Boolean cacheRevalidate;

  private Boolean cacheEncrypt;

  private String cacheKeyEnv;

  private Integer cacheMaxSizeMb;

  private Boolean cacheVersionCheck;

  private String colorMode;

  private String logFormat;

  private boolean jsonOutput;

  public CommonOverrides withFiles(final List<String> files) {
    this.files = files;
    return this;
  }

  public CommonOverrides withDirs(final List<String> dirs) {
    this.dirs = dirs;
    return this;
  }

  public CommonOverrides withExcludeTests(final Boolean excludeTests) {
    this.excludeTests = excludeTests;
    return this;
  }

  public CommonOverrides withEnableVersionHistory(final Boolean enableVersionHistory) {
    this.enableVersionHistory = enableVersionHistory;
    return this;
  }

  public CommonOverrides withDebugDynamicResolution(final boolean debugDynamicResolution) {
    this.debugDynamicResolution = debugDynamicResolution;
    return this;
  }

  public CommonOverrides withExperimentalCandidateEnum(final boolean experimentalCandidateEnum) {
    this.experimentalCandidateEnum = experimentalCandidateEnum;
    return this;
  }

  public CommonOverrides withUnresolvedPolicy(final String unresolvedPolicy) {
    this.unresolvedPolicy = unresolvedPolicy;
    return this;
  }

  public CommonOverrides withMaxCyclomatic(final Integer maxCyclomatic) {
    this.maxCyclomatic = maxCyclomatic;
    return this;
  }

  public CommonOverrides withComplexityStrategy(final String complexityStrategy) {
    this.complexityStrategy = complexityStrategy;
    return this;
  }

  public CommonOverrides withTasksFormat(final String tasksFormat) {
    this.tasksFormat = tasksFormat;
    return this;
  }

  public CommonOverrides withCacheTtl(final Integer cacheTtl) {
    this.cacheTtl = cacheTtl;
    return this;
  }

  public CommonOverrides withCacheRevalidate(final Boolean cacheRevalidate) {
    this.cacheRevalidate = cacheRevalidate;
    return this;
  }

  public CommonOverrides withCacheEncrypt(final Boolean cacheEncrypt) {
    this.cacheEncrypt = cacheEncrypt;
    return this;
  }

  public CommonOverrides withCacheKeyEnv(final String cacheKeyEnv) {
    this.cacheKeyEnv = cacheKeyEnv;
    return this;
  }

  public CommonOverrides withCacheMaxSizeMb(final Integer cacheMaxSizeMb) {
    this.cacheMaxSizeMb = cacheMaxSizeMb;
    return this;
  }

  public CommonOverrides withCacheVersionCheck(final Boolean cacheVersionCheck) {
    this.cacheVersionCheck = cacheVersionCheck;
    return this;
  }

  public CommonOverrides withColorMode(final String colorMode) {
    this.colorMode = colorMode;
    return this;
  }

  public CommonOverrides withLogFormat(final String logFormat) {
    this.logFormat = logFormat;
    return this;
  }

  public CommonOverrides withJsonOutput(final boolean jsonOutput) {
    this.jsonOutput = jsonOutput;
    return this;
  }

  /**
   * Returns the effective log format, considering jsonOutput flag.
   *
   * @return the log format string, or null if not set
   */
  public String getEffectiveLogFormat() {
    return jsonOutput ? "json" : logFormat;
  }

  /**
   * Returns the color mode setting.
   *
   * @return the color mode string, or null if not set
   */
  public String getColorMode() {
    return colorMode;
  }

  @Override
  public void apply(final Config config) {
    if (config == null) {
      return;
    }
    applyIncludePaths(config);
    applyAnalysisOverrides(config);
    applySelectionOverrides(config);
    applyExecutionOverrides(config);
    applyCacheOverrides(config);
    applyOutputOverrides(config);
    applyCliOverrides(config);
  }

  private void applyIncludePaths(final Config config) {
    final List<String> includePaths = new ArrayList<>();
    if (files != null && !files.isEmpty()) {
      includePaths.addAll(files);
    }
    if (dirs != null && !dirs.isEmpty()) {
      includePaths.addAll(dirs);
    }
    if (!includePaths.isEmpty()) {
      if (config.getProject() == null) {
        config.setProject(new Config.ProjectConfig());
      }
      config.getProject().setIncludePaths(includePaths);
    }
  }

  private void applyAnalysisOverrides(final Config config) {
    if (excludeTests != null) {
      if (config.getAnalysis() == null) {
        config.setAnalysis(new Config.AnalysisConfig());
      }
      config.getAnalysis().setExcludeTests(excludeTests);
    }
    if (debugDynamicResolution) {
      if (config.getAnalysis() == null) {
        config.setAnalysis(new Config.AnalysisConfig());
      }
      config.getAnalysis().setDebugDynamicResolution(true);
    }
    if (experimentalCandidateEnum) {
      if (config.getAnalysis() == null) {
        config.setAnalysis(new Config.AnalysisConfig());
      }
      config.getAnalysis().setExperimentalCandidateEnum(true);
    }
  }

  private void applySelectionOverrides(final Config config) {
    if (enableVersionHistory != null && enableVersionHistory) {
      if (config.getSelectionRules() == null) {
        config.setSelectionRules(new Config.SelectionRules());
      }
      final Config.SelectionRules rules = config.getSelectionRules();
      final Config.SelectionRules.VersionHistoryConfig versionHistory = rules.getVersionHistory();
      versionHistory.setEnabled(true);
      rules.setVersionHistory(versionHistory);
    }
    if (maxCyclomatic != null || (complexityStrategy != null && !complexityStrategy.isBlank())) {
      if (config.getSelectionRules() == null) {
        config.setSelectionRules(new Config.SelectionRules());
      }
      final var complexityConfig = config.getSelectionRules().getComplexity();
      if (maxCyclomatic != null) {
        complexityConfig.setMaxCyclomatic(maxCyclomatic);
      }
      if (complexityStrategy != null && !complexityStrategy.isBlank()) {
        complexityConfig.setStrategy(complexityStrategy);
      }
    }
  }

  private void applyExecutionOverrides(final Config config) {
    if (unresolvedPolicy != null && !unresolvedPolicy.isBlank()) {
      if (config.getExecution() == null) {
        config.setExecution(new Config.ExecutionConfig());
      }
      config.getExecution().setUnresolvedPolicy(unresolvedPolicy);
    }
  }

  private void applyCacheOverrides(final Config config) {
    if (cacheTtl != null
        || cacheRevalidate != null
        || cacheEncrypt != null
        || cacheKeyEnv != null
        || cacheMaxSizeMb != null
        || cacheVersionCheck != null) {
      final Config.CacheConfig cache = config.getCache();
      if (cacheTtl != null) {
        cache.setTtlDays(cacheTtl);
      }
      if (cacheRevalidate != null) {
        cache.setRevalidate(cacheRevalidate);
      }
      if (cacheEncrypt != null) {
        cache.setEncrypt(cacheEncrypt);
      }
      if (cacheKeyEnv != null) {
        cache.setEncryptionKeyEnv(cacheKeyEnv);
      }
      if (cacheMaxSizeMb != null) {
        cache.setMaxSizeMb(cacheMaxSizeMb);
      }
      if (cacheVersionCheck != null) {
        cache.setVersionCheck(cacheVersionCheck);
      }
    }
  }

  private void applyOutputOverrides(final Config config) {
    if (tasksFormat != null && !tasksFormat.isBlank()) {
      config.getOutput().getFormat().setTasks(tasksFormat);
    }
    final String effectiveLogFormat = getEffectiveLogFormat();
    if (effectiveLogFormat != null) {
      final Config.LogConfig logConfig = config.getLog();
      config.setLog(logConfig);
      logConfig.setFormat(effectiveLogFormat);
    }
  }

  private void applyCliOverrides(final Config config) {
    if (colorMode != null) {
      if (config.getCli() == null) {
        config.setCli(new Config.CliConfig());
      }
      config.getCli().setColor(colorMode);
    }
  }
}
