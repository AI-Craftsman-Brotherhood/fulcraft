package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves run-scoped artifact directories on the local filesystem. */
public record FsArtifactStore(Path runRoot) {

  private static final String ACTIONS_DIR = "actions";
  private static final String PLUGIN_ID = "pluginId";
  private static final String NODE_ID = "nodeId";

  public FsArtifactStore {
    Objects.requireNonNull(runRoot, nullArgumentMessage("runRoot"));
  }

  public static FsArtifactStore from(final Path runRoot) {
    return new FsArtifactStore(runRoot);
  }

  public Path actionsRoot() throws IOException {
    return ensureDirectory(runRoot.resolve(ACTIONS_DIR));
  }

  public Path actions(final String pluginId) throws IOException {
    validatePathSegment(pluginId, PLUGIN_ID);
    return ensureChildDirectory(actionsRoot(), pluginId, PLUGIN_ID);
  }

  public Path actions(final String pluginId, final String nodeId) throws IOException {
    validatePathSegment(nodeId, NODE_ID);
    return ensureChildDirectory(actions(pluginId), nodeId, NODE_ID);
  }

  private static Path ensureDirectory(final Path directory) throws IOException {
    Files.createDirectories(directory);
    return directory;
  }

  private static Path ensureChildDirectory(
      final Path parentDirectory, final String childName, final String argumentName)
      throws IOException {
    return ensureDirectory(resolveChildDirectory(parentDirectory, childName, argumentName));
  }

  private static Path resolveChildDirectory(
      final Path parentDirectory, final String childName, final String argumentName) {
    final Path candidate = parentDirectory.resolve(childName).normalize();
    final Path normalizedParent = parentDirectory.toAbsolutePath().normalize();
    final Path normalizedCandidate = candidate.toAbsolutePath().normalize();
    if (!normalizedCandidate.startsWith(normalizedParent)) {
      throw invalidPathMessage(argumentName, "must resolve inside " + parentDirectory);
    }
    return candidate;
  }

  private static void validatePathSegment(final String value, final String argumentName) {
    Objects.requireNonNull(value, nullArgumentMessage(argumentName));
    if (value.isBlank()) {
      throw invalidPathMessage(argumentName, "must not be blank");
    }
    if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
      throw invalidPathMessage(argumentName, "must not contain path separators");
    }
  }

  private static IllegalArgumentException invalidPathMessage(
      final String argumentName, final String details) {
    return new IllegalArgumentException(
        MessageSource.getMessage("infra.common.error.message", argumentName + " " + details));
  }

  private static String nullArgumentMessage(final String argumentName) {
    return MessageSource.getMessage("infra.common.error.argument_null", argumentName);
  }
}
