package com.craftsmanbro.fulcraft.kernel.workflow;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.InvalidConfigurationException;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.workflow.model.WorkflowDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads workflow definitions for workflow-driven execution.
 *
 * <p>When {@code pipeline.workflow_file} is configured, the loader reads that file from the project
 * root. Otherwise it falls back to the bundled default workflow resource. If the bundled default is
 * absent, the loader returns {@link Optional#empty()} to signal that no workflow is configured.
 */
public class WorkflowLoader {

  private static final String DEFAULT_WORKFLOW_RESOURCE_PATH = "workflows/default-workflow.json";

  /**
   * Loads workflow definition if configured.
   *
   * @param config configuration
   * @param projectRoot project root used to resolve relative workflow paths
   * @return workflow definition when configured
   */
  public Optional<WorkflowDefinition> load(final Config config, final Path projectRoot) {
    Objects.requireNonNull(config, msg("kernel.workflow.loader.error.config_null"));
    Objects.requireNonNull(projectRoot, msg("kernel.workflow.loader.error.project_root_null"));
    final String workflowFile = config.getPipeline().getWorkflowFile();
    if (workflowFile == null || workflowFile.isBlank()) {
      return loadDefaultWorkflow();
    }
    final Path workflowPath = resolveWorkflowPath(projectRoot, workflowFile);
    if (!Files.isRegularFile(workflowPath)) {
      throw new InvalidConfigurationException(
          msg("kernel.workflow.loader.error.file_not_found", workflowPath.toAbsolutePath()));
    }
    final String workflowJson = readWorkflowFile(workflowPath);
    return Optional.of(parseDefinition(workflowJson, workflowPath));
  }

  private Optional<WorkflowDefinition> loadDefaultWorkflow() {
    final InputStream defaultWorkflowResourceStream =
        getClass().getClassLoader().getResourceAsStream(DEFAULT_WORKFLOW_RESOURCE_PATH);
    if (defaultWorkflowResourceStream == null) {
      // Treat a missing bundled default as "no workflow configured".
      return Optional.empty();
    }
    try (InputStream resourceInput = defaultWorkflowResourceStream) {
      final String workflowJson = new String(resourceInput.readAllBytes(), StandardCharsets.UTF_8);
      return Optional.of(parseDefinition(workflowJson, Path.of(DEFAULT_WORKFLOW_RESOURCE_PATH)));
    } catch (IOException e) {
      throw new InvalidConfigurationException(
          msg("kernel.workflow.loader.error.default_resource_read_failed"), e);
    }
  }

  private Path resolveWorkflowPath(final Path projectRoot, final String workflowFile) {
    final Path configuredWorkflowPath = Path.of(workflowFile);
    if (configuredWorkflowPath.isAbsolute()) {
      return configuredWorkflowPath.normalize();
    }
    return projectRoot.resolve(configuredWorkflowPath).normalize();
  }

  private String readWorkflowFile(final Path workflowPath) {
    try {
      return Files.readString(workflowPath);
    } catch (IOException e) {
      throw new InvalidConfigurationException(
          msg("kernel.workflow.loader.error.file_read_failed", workflowPath.toAbsolutePath()), e);
    }
  }

  private WorkflowDefinition parseDefinition(final String jsonText, final Path workflowPath) {
    final ObjectMapper objectMapper = JsonMapperFactory.create();
    try {
      final WorkflowDefinition parsedWorkflowDefinition =
          objectMapper.readValue(jsonText, WorkflowDefinition.class);
      // Reject literal JSON null so callers see an invalid workflow configuration.
      if (parsedWorkflowDefinition == null) {
        throw new WorkflowConfigurationException(
            msg("kernel.workflow.loader.error.definition_empty"));
      }
      return parsedWorkflowDefinition;
    } catch (JacksonException e) {
      throw new InvalidConfigurationException(
          msg(
              "kernel.workflow.loader.error.file_parse_failed",
              workflowPath.toAbsolutePath(),
              e.getOriginalMessage()),
          e);
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
