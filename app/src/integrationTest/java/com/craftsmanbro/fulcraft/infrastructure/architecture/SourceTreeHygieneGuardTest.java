package com.craftsmanbro.fulcraft.infrastructure.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SourceTreeHygieneGuardTest {

  private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
  private static final Path INFRA_SOURCE_ROOT =
      MAIN_SOURCE_ROOT.resolve("com/craftsmanbro/fulcraft/infrastructure");
  private static final Set<String> CANONICAL_FEATURE_DIRS =
      Set.of("contract", "model", "impl", "exception", "internal");
  private static final Set<String> REQUIRED_BASE_FEATURE_DIRS = Set.of("contract", "model", "impl");
  private static final Map<String, Set<String>> PROVIDER_FIRST_FEATURE_PROVIDERS =
      Map.of("auth", Set.of("aws"));

  @Test
  void mainSourceTreeMustNotContainCompiledClassFiles() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> stream = Files.walk(MAIN_SOURCE_ROOT)) {
      stream
          .filter(path -> path.toString().endsWith(".class"))
          .forEach(path -> violations.add(MAIN_SOURCE_ROOT.relativize(path).toString()));
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "src/main/java must not contain compiled .class files:\n"
                + String.join("\n", violations));
  }

  @Test
  void infrastructureFeatureMustNotHaveJavaFilesDirectlyUnderFeatureRoot() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> features = Files.list(INFRA_SOURCE_ROOT)) {
      for (Path feature : features.filter(Files::isDirectory).toList()) {
        try (Stream<Path> children = Files.list(feature)) {
          children
              .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
              .forEach(path -> violations.add(INFRA_SOURCE_ROOT.relativize(path).toString()));
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "infrastructure feature root must not contain direct .java files:\n"
                + String.join("\n", violations));
  }

  @Test
  void infrastructureFeatureTopLevelDirectoriesMustBeCanonical() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> features = Files.list(INFRA_SOURCE_ROOT)) {
      for (Path feature : features.filter(Files::isDirectory).toList()) {
        String featureName = feature.getFileName().toString();
        Set<String> allowedTopLevelDirs =
            PROVIDER_FIRST_FEATURE_PROVIDERS.getOrDefault(featureName, CANONICAL_FEATURE_DIRS);
        try (Stream<Path> children = Files.list(feature)) {
          children
              .filter(Files::isDirectory)
              .filter(path -> !allowedTopLevelDirs.contains(path.getFileName().toString()))
              .forEach(path -> violations.add(INFRA_SOURCE_ROOT.relativize(path).toString()));
        }
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "infrastructure feature top-level directories must be canonical (contract/model/impl/exception/internal), "
                + "except provider-first features (auth/aws):\n"
                + String.join("\n", violations));
  }

  @Test
  void infrastructureFeatureMustHaveRequiredBaseDirectories() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> features = Files.list(INFRA_SOURCE_ROOT)) {
      for (Path feature : features.filter(Files::isDirectory).toList()) {
        String featureName = feature.getFileName().toString();
        Set<String> providers = PROVIDER_FIRST_FEATURE_PROVIDERS.get(featureName);
        if (providers != null) {
          for (String provider : providers) {
            Path providerRoot = feature.resolve(provider);
            if (!Files.isDirectory(providerRoot)) {
              violations.add(
                  INFRA_SOURCE_ROOT.relativize(feature).toString()
                      + " missing provider directory: "
                      + provider);
              continue;
            }
            addDirectJavaViolations(providerRoot, violations);
            addUnexpectedChildDirectoryViolations(providerRoot, CANONICAL_FEATURE_DIRS, violations);
            addMissingRequiredDirectoryViolations(providerRoot, violations);
          }
          continue;
        }
        addMissingRequiredDirectoryViolations(feature, violations);
      }
    }

    assertTrue(
        violations.isEmpty(),
        () ->
            "infrastructure features must provide base directories (contract/model/impl). "
                + "Provider-first features (auth/aws) must provide them under provider directory:\n"
                + String.join("\n", violations));
  }

  private static void addDirectJavaViolations(Path root, List<String> violations)
      throws IOException {
    try (Stream<Path> children = Files.list(root)) {
      children
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .forEach(path -> violations.add(INFRA_SOURCE_ROOT.relativize(path).toString()));
    }
  }

  private static void addUnexpectedChildDirectoryViolations(
      Path root, Set<String> allowedDirectories, List<String> violations) throws IOException {
    try (Stream<Path> children = Files.list(root)) {
      children
          .filter(Files::isDirectory)
          .map(path -> path.getFileName().toString())
          .filter(name -> !allowedDirectories.contains(name))
          .forEach(
              name -> violations.add(INFRA_SOURCE_ROOT.relativize(root.resolve(name)).toString()));
    }
  }

  private static void addMissingRequiredDirectoryViolations(Path root, List<String> violations)
      throws IOException {
    Set<String> childNames;
    try (Stream<Path> children = Files.list(root)) {
      childNames =
          children
              .filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .collect(java.util.stream.Collectors.toSet());
    }
    for (String required : REQUIRED_BASE_FEATURE_DIRS) {
      if (!childNames.contains(required)) {
        violations.add(
            INFRA_SOURCE_ROOT.relativize(root).toString() + " missing directory: " + required);
      }
    }
  }
}
