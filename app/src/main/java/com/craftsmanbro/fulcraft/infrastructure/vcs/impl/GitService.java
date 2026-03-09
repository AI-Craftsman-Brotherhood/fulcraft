package com.craftsmanbro.fulcraft.infrastructure.vcs.impl;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.vcs.contract.VcsCommitCountPort;
import com.craftsmanbro.fulcraft.infrastructure.vcs.model.CommitCount;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.math.NumberUtils;

public class GitService implements VcsCommitCountPort {

  private static final long TIMEOUT_SECONDS = 5;

  private final Path projectRoot;

  private final Map<String, Integer> commitCountCache = new ConcurrentHashMap<>();

  private final AtomicReference<List<Path>> sourceRootPaths = new AtomicReference<>(List.of());

  public GitService(final Path projectRoot) {
    this.projectRoot =
        Objects.requireNonNull(
            projectRoot,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "Project root must not be null"));
  }

  @Override
  public void setSourceRootPaths(final List<String> sourceRootPaths) {
    if (sourceRootPaths == null || sourceRootPaths.isEmpty()) {
      this.sourceRootPaths.set(List.of());
      return;
    }
    final List<Path> normalized = new ArrayList<>();
    for (final String sourceRootPath : sourceRootPaths) {
      if (sourceRootPath == null || sourceRootPath.isBlank()) {
        continue;
      }
      normalized.add(Paths.get(sourceRootPath).normalize());
    }
    this.sourceRootPaths.set(List.copyOf(normalized));
  }

  @Override
  public CommitCount resolveCommitCount(final String filePath) {
    if (filePath == null || filePath.isBlank()) {
      return CommitCount.zero();
    }
    final int count = commitCountCache.computeIfAbsent(filePath, this::executeGitCommitCount);
    return new CommitCount(count);
  }

  @Override
  public int getCommitCount(final String filePath) {
    return resolveCommitCount(filePath).value();
  }

  private int executeGitCommitCount(final String filePath) {
    final List<String> candidates = buildCandidatePaths(filePath);
    try {
      for (final String candidate : candidates) {
        final Integer count = executeGitCommitCountForPath(candidate);
        if (count != null) {
          return count;
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Git commit count interrupted for " + filePath));
      return 0;
    } catch (Exception e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Git commit count failed for " + filePath + ": " + e.getMessage()));
      return 0;
    }
    Logger.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.log.message", "Git commit count failed for " + filePath));
    return 0;
  }

  private Integer executeGitCommitCountForPath(final String candidate)
      throws IOException, InterruptedException {
    final var process = startGitProcess(candidate);
    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Git commit count timed out for " + candidate));
      return null;
    }
    if (process.exitValue() != 0) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message",
              "Git process exited with non-zero code for " + candidate));
      return null;
    }
    try (var reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      final var line = reader.readLine();
      return NumberUtils.toInt(line != null ? line.trim() : "0", 0);
    }
  }

  private List<String> buildCandidatePaths(final String filePath) {
    final Path inputPath;
    try {
      inputPath = Paths.get(filePath).normalize();
    } catch (Exception e) {
      return List.of(filePath);
    }
    final List<String> candidates = new ArrayList<>();
    if (inputPath.isAbsolute()) {
      if (inputPath.startsWith(projectRoot)) {
        candidates.add(projectRoot.relativize(inputPath).toString());
      } else {
        candidates.add(inputPath.toString());
      }
      return candidates;
    }
    if (Files.exists(projectRoot.resolve(inputPath))) {
      candidates.add(inputPath.toString());
      return candidates;
    }
    final List<String> resolvedCandidates = new ArrayList<>();
    for (final Path sourceRootPath : sourceRootPaths.get()) {
      final Path resolvedCandidatePath = sourceRootPath.resolve(inputPath).normalize();
      if (Files.exists(projectRoot.resolve(resolvedCandidatePath))) {
        resolvedCandidates.add(resolvedCandidatePath.toString());
      }
    }
    if (!resolvedCandidates.isEmpty()) {
      resolvedCandidates.add(inputPath.toString());
      return resolvedCandidates;
    }
    candidates.add(inputPath.toString());
    return candidates;
  }

  protected Process startGitProcess(final String filePath) throws IOException {
    final var processBuilder = new ProcessBuilder("git", "rev-list", "--count", "HEAD", "--", filePath);
    processBuilder.directory(projectRoot.toFile());
    processBuilder.redirectErrorStream(true);
    return processBuilder.start();
  }
}
