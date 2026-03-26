package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/** Loads simple string key/value pairs from resource configuration files. */
public class ExternalConfigValueLoader {

  public Map<String, String> load(final Path resourcesRoot) {
    final Map<String, String> values = new LinkedHashMap<>();
    if (resourcesRoot == null || !Files.isDirectory(resourcesRoot)) {
      return values;
    }
    try (Stream<Path> paths = Files.walk(resourcesRoot)) {
      paths
          .filter(Files::isRegularFile)
          .sorted(PathOrderAdapter.STABLE)
          .forEach(path -> loadFile(values, path));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage(
              "analysis.external_config.scan_failed", resourcesRoot, e.getMessage()));
    }
    return values;
  }

  private void loadFile(final Map<String, String> values, final Path path) {
    final Path fileName = path.getFileName();
    if (fileName == null) {
      return;
    }
    final String lowerCaseFileName = fileName.toString().toLowerCase(java.util.Locale.ROOT);
    if (lowerCaseFileName.endsWith(".properties")) {
      loadProperties(values, path);
      return;
    }
    if (lowerCaseFileName.endsWith(".yml") || lowerCaseFileName.endsWith(".yaml")) {
      loadYaml(values, path);
    }
  }

  private void loadProperties(final Map<String, String> values, final Path path) {
    final Properties props = new Properties();
    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (IOException e) {
      Logger.debug(
          MessageSource.getMessage("analysis.external_config.read_properties_failed", path));
      return;
    }
    for (final String key : props.stringPropertyNames()) {
      final String value = props.getProperty(key);
      putIfSimple(values, key, value);
    }
  }

  private void loadYaml(final Map<String, String> values, final Path path) {
    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
      lines.forEach(
          line -> {
            if (line.isBlank()) {
              return;
            }
            final String rawLine = line;
            final String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
              return;
            }
            if (!rawLine.equals(trimmed)) {
              return;
            }
            final int colon = trimmed.indexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
              return;
            }
            final String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (value.isEmpty()) {
              return;
            }
            if (value.startsWith("{")
                || value.startsWith("[")
                || value.startsWith("|")
                || value.startsWith(">")) {
              return;
            }
            final String valueWithoutInlineComment = stripInlineComment(value);
            final String normalizedValue = stripQuotes(valueWithoutInlineComment);
            value = normalizedValue;
            putIfSimple(values, key, value);
          });
    } catch (IOException e) {
      Logger.debug(MessageSource.getMessage("analysis.external_config.read_yaml_failed", path));
    }
  }

  private void putIfSimple(final Map<String, String> values, final String key, final String value) {
    if (key == null || key.isBlank() || value == null || value.isBlank()) {
      return;
    }
    if (value.contains("${")) {
      return;
    }
    values.putIfAbsent(key, value);
  }

  private String stripQuotes(final String value) {
    final String trimmed = value.trim();
    if (trimmed.length() >= 2
        && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
            || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
      return trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  private String stripInlineComment(final String value) {
    boolean inSingle = false;
    boolean inDouble = false;
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
      } else if (c == '"' && !inSingle) {
        inDouble = !inDouble;
      } else if (c == '#' && !inSingle && !inDouble && (i == 0 || value.charAt(i - 1) == ' ')) {
        return value.substring(0, i).trim();
      }
    }
    return value;
  }
}
