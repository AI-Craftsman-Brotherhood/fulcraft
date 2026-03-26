package com.craftsmanbro.fulcraft.kernel.pipeline.interceptor;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.config.validator.ConfigValidator;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates project configuration before the ANALYZE phase.
 *
 * <p>This interceptor runs at the very beginning of the pipeline to ensure that all required
 * configuration is available and valid before analysis begins.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Validate that the project root exists and is accessible
 *   <li>Ensure required configuration values are present
 *   <li>Log configuration summary for debugging
 * </ul>
 */
public class ConfigLoaderInterceptor implements PhaseInterceptor {

  private static final LoggerPort LOG = LoggerPortProvider.getLogger(ConfigLoaderInterceptor.class);

  private static final String ID = "config-loader";

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String phase() {
    return PipelineNodeIds.ANALYZE;
  }

  @Override
  public Hook hook() {
    return Hook.PRE;
  }

  @Override
  public int order() {
    // Run very early to ensure config is ready for other interceptors
    return 10;
  }

  @Override
  public boolean supports(final Config config) {
    // Always enabled - configuration loading is essential
    return true;
  }

  @Override
  public void apply(final RunContext context) {
    LOG.debug(msg("kernel.interceptor.config_loader.log.validating"));
    final Path projectRoot = validateProjectRoot(context);
    if (projectRoot == null) {
      return;
    }
    final Config config = validateLoadedConfig(context);
    if (config == null || !validateConfig(context, config)) {
      return;
    }
    logConfigurationSummary(config, projectRoot);
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private static Path validateProjectRoot(final RunContext context) {
    final Path projectRoot = context.getProjectRoot();
    if (projectRoot == null) {
      addError(context, "kernel.interceptor.config_loader.error.project_root_not_set");
      return null;
    }
    if (!Files.exists(projectRoot)) {
      addError(
          context, "kernel.interceptor.config_loader.error.project_root_not_exists", projectRoot);
      return null;
    }
    if (!Files.isDirectory(projectRoot)) {
      addError(
          context,
          "kernel.interceptor.config_loader.error.project_root_not_directory",
          projectRoot);
      return null;
    }
    return projectRoot;
  }

  private static Config validateLoadedConfig(final RunContext context) {
    final Config config = context.getConfig();
    if (config == null) {
      addError(context, "kernel.interceptor.config_loader.error.config_not_loaded");
      return null;
    }
    return config;
  }

  private static boolean validateConfig(final RunContext context, final Config config) {
    try {
      new ConfigValidator().validateParsedConfig(config);
      return true;
    } catch (InvalidConfigurationException e) {
      addError(context, "kernel.interceptor.config_loader.error.validation_failed", e.getMessage());
      return false;
    }
  }

  private static void logConfigurationSummary(final Config config, final Path projectRoot) {
    LOG.debug(
        msg(
            "kernel.interceptor.config_loader.log.config_loaded_for_project",
            resolveProjectId(config)));
    LOG.debug(
        msg("kernel.interceptor.config_loader.log.project_root"), projectRoot.toAbsolutePath());
  }

  private static String resolveProjectId(final Config config) {
    return config.getProject() != null
        ? config.getProject().getId()
        : msg("kernel.interceptor.config_loader.value.unknown");
  }

  private static void addError(final RunContext context, final String key, final Object... args) {
    context.addError(msg(key, args));
  }
}
