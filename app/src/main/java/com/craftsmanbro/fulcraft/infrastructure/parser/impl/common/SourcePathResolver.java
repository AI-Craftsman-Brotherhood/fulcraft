package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * プロジェクトのルートパスから、メインソースとテストソースのディレクトリを自動的に解決するクラス。
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.SourcePathResolver} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public class SourcePathResolver {

  private static final String JAVA_EXTENSION = ".java";
  private static final int GRADLE_SOURCE_CONTEXT_WINDOW = 400;
  private static final Pattern GRADLE_STRING_PATTERN = Pattern.compile("\"([^\"]+)\"|'([^']+)'");

  private static final Comparator<Path> STABLE_PATH_ORDER =
      Comparator.comparing(SourcePathResolver::normalizedPathKey);

  private static final List<PathOption> MAIN_SOURCE_CANDIDATES =
      List.of(
          new PathOption("src/main/java", path -> true),
          new PathOption("app/src/main/java", path -> true),
          new PathOption("src", SourcePathResolver::containsNonTestJavaSources),
          new PathOption(null, SourcePathResolver::containsTopLevelJavaFiles));

  private static final List<PathOption> TEST_SOURCE_CANDIDATES =
      List.of(
          new PathOption("src/test/java", path -> true),
          new PathOption("test", path -> true),
          new PathOption("app/src/test/java", path -> true));

  public SourceDirectories resolve(final Path rootPath) {
    return resolve(rootPath, null);
  }

  public SourceDirectories resolve(final Path rootPath, final Config config) {
    Objects.requireNonNull(
        rootPath,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "rootPath must not be null"));
    final Config.AnalysisConfig analysisConfig = config != null ? config.getAnalysis() : null;
    final boolean strictMode = analysisConfig != null && analysisConfig.isStrictMode();
    if (strictMode) {
      return resolveStrict(rootPath, analysisConfig);
    } else {
      return resolveAuto(rootPath, config);
    }
  }

  private SourceDirectories resolveStrict(
      final Path rootPath, final Config.AnalysisConfig analysisConfig) {
    final List<String> sourceRootPaths = analysisConfig.getSourceRootPaths();
    final StrictMainSourceResult strictMainSourceResult =
        findStrictMainSource(rootPath, sourceRootPaths);
    final Path mainSource =
        strictMainSourceResult
            .mainSource()
            .orElseThrow(
                () ->
                    strictModeNoValidRootException(
                        sourceRootPaths, strictMainSourceResult.searchedPaths()));
    Logger.info(MessageSource.getMessage("analysis.source_path.strict.found_root", mainSource));
    final Optional<Path> testSource = deriveTestSource(rootPath, mainSource);
    updateSourceRootsFromStrict(analysisConfig, rootPath, mainSource);
    return new SourceDirectories(Optional.of(mainSource), testSource);
  }

  private StrictMainSourceResult findStrictMainSource(
      final Path rootPath, final List<String> sourceRootPaths) {
    final List<String> searchedPaths = new ArrayList<>();
    for (final String pathStr : sourceRootPaths) {
      final Optional<Path> candidate = resolveStrictCandidate(rootPath, pathStr, searchedPaths);
      if (candidate.isPresent()) {
        return new StrictMainSourceResult(candidate, searchedPaths);
      }
    }
    return new StrictMainSourceResult(Optional.empty(), searchedPaths);
  }

  private Optional<Path> resolveStrictCandidate(
      final Path rootPath, final String pathStr, final List<String> searchedPaths) {
    if (pathStr == null || pathStr.isBlank()) {
      return Optional.empty();
    }
    final Path candidate = rootPath.resolve(pathStr);
    searchedPaths.add(candidate.toString());
    if (Files.isDirectory(candidate)) {
      return Optional.of(candidate);
    }
    return Optional.empty();
  }

  private IllegalStateException strictModeNoValidRootException(
      final List<String> sourceRootPaths, final List<String> searchedPaths) {
    final String searched = searchedPaths.isEmpty() ? "-" : String.join("\n    ", searchedPaths);
    final String msg =
        MessageSource.getMessage(
            "analysis.source_path.strict.no_valid_root", sourceRootPaths, searched);
    return new IllegalStateException(msg);
  }

  private Optional<Path> deriveTestSource(final Path rootPath, final Path mainSource) {
    final Path relative = rootPath.relativize(mainSource).normalize();
    final Optional<Path> derived = replacePathSegment(relative, "main", "test");
    if (derived.isPresent()) {
      final Path testCandidate = rootPath.resolve(derived.get());
      if (Files.isDirectory(testCandidate)) {
        return Optional.of(testCandidate);
      }
    }
    for (final var option : TEST_SOURCE_CANDIDATES) {
      final Path candidate = option.resolve(rootPath);
      if (Files.isDirectory(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private Optional<Path> replacePathSegment(final Path path, final String from, final String to) {
    final int nameCount = path.getNameCount();
    for (int i = 0; i < nameCount; i++) {
      if (path.getName(i).toString().equals(from)) {
        Path replaced = i == 0 ? path.getFileSystem().getPath(to) : path.subpath(0, i).resolve(to);
        if (i + 1 < nameCount) {
          replaced = replaced.resolve(path.subpath(i + 1, nameCount));
        }
        return Optional.of(replaced);
      }
    }
    return Optional.empty();
  }

  private SourceDirectories resolveAuto(final Path rootPath, final Config config) {
    final var fromBuildFiles = resolveFromBuildFiles(rootPath);
    var main = fromBuildFiles.flatMap(SourceDirectories::mainSource);
    var test = fromBuildFiles.flatMap(SourceDirectories::testSource);
    if (main.isEmpty()) {
      main = resolveFromConfiguredPaths(rootPath, config);
    }
    if (main.isEmpty()) {
      main = resolveFromCandidates(rootPath, MAIN_SOURCE_CANDIDATES);
    }
    if (main.isEmpty()) {
      main = resolveBySearching(rootPath, "src", SourcePathResolver::containsNonTestJavaSources);
    }
    if (test.isEmpty()) {
      test = resolveFromCandidates(rootPath, TEST_SOURCE_CANDIDATES);
    }
    if (test.isEmpty()) {
      test = resolveBySearching(rootPath, "test", path -> true);
    }
    updateSourceRoots(config, rootPath, main);
    return new SourceDirectories(main, test);
  }

  private Optional<Path> resolveFromConfiguredPaths(final Path rootPath, final Config config) {
    if (config == null || config.getAnalysis() == null) {
      return Optional.empty();
    }
    final List<String> paths = config.getAnalysis().getSourceRootPaths();
    if (paths == null || paths.isEmpty()) {
      return Optional.empty();
    }
    for (final String pathStr : paths) {
      if (pathStr == null || pathStr.isBlank()) {
        continue;
      }
      final Path candidate = rootPath.resolve(pathStr);
      if (Files.isDirectory(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private Optional<SourceDirectories> resolveFromBuildFiles(final Path rootPath) {
    final Optional<SourceDirectories> gradle = resolveFromGradleBuild(rootPath);
    if (gradle.isPresent()) {
      return gradle;
    }
    return resolveFromPom(rootPath);
  }

  private Optional<SourceDirectories> resolveFromGradleBuild(final Path rootPath) {
    final List<Path> buildFiles =
        List.of(rootPath.resolve("build.gradle"), rootPath.resolve("build.gradle.kts"));
    for (final Path buildFile : buildFiles) {
      if (!Files.isRegularFile(buildFile)) {
        continue;
      }
      try {
        final String content = Files.readString(buildFile);
        final SourceRoots roots = parseGradleSourceRoots(content);
        final Optional<Path> main = findFirstExisting(rootPath, roots.main());
        final Optional<Path> test = findFirstExisting(rootPath, roots.test());
        if (main.isPresent() || test.isPresent()) {
          return Optional.of(new SourceDirectories(main, test));
        }
      } catch (IOException e) {
        Logger.debug(
            MessageSource.getMessage(
                "analysis.source_path.gradle.read_failed", buildFile, e.getMessage()));
      }
    }
    return Optional.empty();
  }

  private Optional<SourceDirectories> resolveFromPom(final Path rootPath) {
    final Path pom = rootPath.resolve("pom.xml");
    if (!Files.isRegularFile(pom)) {
      return Optional.empty();
    }
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      configureSecurityFeatures(factory);
      final Document document = factory.newDocumentBuilder().parse(pom.toFile());
      final String mainText = textContent(document, "sourceDirectory");
      final String testText = textContent(document, "testSourceDirectory");
      final Optional<Path> main = resolvePathIfExists(rootPath, mainText);
      final Optional<Path> test = resolvePathIfExists(rootPath, testText);
      if (main.isPresent() || test.isPresent()) {
        return Optional.of(new SourceDirectories(main, test));
      }
    } catch (IOException | ParserConfigurationException | SAXException e) {
      Logger.debug(
          MessageSource.getMessage("analysis.source_path.pom.parse_failed", e.getMessage()));
    }
    return Optional.empty();
  }

  private void configureSecurityFeatures(final DocumentBuilderFactory factory) {
    try {
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    } catch (Exception e) {
      Logger.debug(
          MessageSource.getMessage(
              "analysis.source_path.xml.security_unsupported", e.getMessage()));
    }
  }

  private String textContent(final Document document, final String tagName) {
    final NodeList nodes = document.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      return null;
    }
    final String text = nodes.item(0).getTextContent();
    return text != null ? text.trim() : null;
  }

  private Optional<Path> resolvePathIfExists(final Path rootPath, final String pathText) {
    if (pathText == null || pathText.isBlank()) {
      return Optional.empty();
    }
    final Path candidate = rootPath.resolve(pathText.trim());
    if (Files.isDirectory(candidate)) {
      return Optional.of(candidate);
    }
    return Optional.empty();
  }

  private Optional<Path> findFirstExisting(final Path rootPath, final List<String> candidates) {
    for (final String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      final Path resolved = rootPath.resolve(candidate).normalize();
      if (Files.isDirectory(resolved)) {
        return Optional.of(resolved);
      }
    }
    return Optional.empty();
  }

  private SourceRoots parseGradleSourceRoots(final String content) {
    final List<String> main = new ArrayList<>();
    final List<String> test = new ArrayList<>();
    final String lowerContent = content.toLowerCase(Locale.ROOT);
    final boolean hasSourceSets = lowerContent.contains("sourcesets");
    final Matcher matcher = GRADLE_STRING_PATTERN.matcher(content);
    while (matcher.find()) {
      final String value = extractGradleLiteral(matcher);
      if (!looksLikeSourcePath(value)) {
        continue;
      }
      final String context = extractGradleContext(lowerContent, matcher.start());
      classifyGradleSourcePath(hasSourceSets, context, value, main, test);
    }
    return new SourceRoots(main, test);
  }

  private String extractGradleLiteral(final Matcher matcher) {
    return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
  }

  private String extractGradleContext(final String lowerContent, final int matchStart) {
    final int fromIndex = Math.max(0, matchStart - GRADLE_SOURCE_CONTEXT_WINDOW);
    return lowerContent.substring(fromIndex, matchStart);
  }

  private void classifyGradleSourcePath(
      final boolean hasSourceSets,
      final String context,
      final String value,
      final List<String> main,
      final List<String> test) {
    if (!hasSourceSets) {
      return;
    }
    if (isGradleTestSource(context, value)) {
      test.add(value);
      return;
    }
    if (isGradleMainSource(context, value)) {
      main.add(value);
      return;
    }
    if (hasGradleSrcDirContext(context)) {
      main.add(value);
    }
  }

  private boolean isGradleTestSource(final String context, final String value) {
    final boolean valueLooksTest = valueContainsSegments(value, "test", "src");
    return (context.contains("test") || valueLooksTest)
        && (hasGradleSrcDirContext(context) || valueLooksTest);
  }

  private boolean isGradleMainSource(final String context, final String value) {
    final boolean valueLooksMain = valueContainsSegments(value, "main", "src");
    return (context.contains("main") || valueLooksMain)
        && (hasGradleSrcDirContext(context) || valueLooksMain);
  }

  private boolean hasGradleSrcDirContext(final String context) {
    return context.contains("srcdir") || context.contains("srcdirs");
  }

  private boolean valueContainsSegments(
      final String value, final String firstSegment, final String secondSegment) {
    final String valueLower = value.toLowerCase(Locale.ROOT);
    return valueLower.contains(firstSegment) && valueLower.contains(secondSegment);
  }

  private boolean looksLikeSourcePath(final String value) {
    final String normalized = value.toLowerCase(Locale.ROOT);
    return normalized.contains("src") && normalized.contains("java");
  }

  private void updateSourceRoots(
      final Config config, final Path rootPath, final Optional<Path> main) {
    if (config == null || config.getAnalysis() == null) {
      return;
    }
    if (main.isEmpty()) {
      return;
    }
    final String relative = rootPath.relativize(main.get()).toString();
    config.getAnalysis().setSourceRootPaths(List.of(relative));
  }

  private void updateSourceRootsFromStrict(
      final Config.AnalysisConfig analysisConfig, final Path rootPath, final Path mainSource) {
    if (analysisConfig == null || mainSource == null) {
      return;
    }
    final String relative = rootPath.relativize(mainSource).toString();
    analysisConfig.setSourceRootPaths(List.of(relative));
  }

  private Optional<Path> resolveFromCandidates(
      final Path rootPath, final List<PathOption> candidates) {
    for (final var option : candidates) {
      final var candidate = option.resolve(rootPath);
      if (Files.isDirectory(candidate) && option.condition().test(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private Optional<Path> resolveBySearching(
      final Path rootPath, final String name, final Predicate<Path> condition) {
    try (Stream<Path> stream = Files.walk(rootPath, 3)) {
      return stream
          .filter(Files::isDirectory)
          .filter(
              p -> {
                final Path fileName = p.getFileName();
                return fileName != null && fileName.toString().equals(name);
              })
          .filter(condition)
          .sorted(STABLE_PATH_ORDER)
          .findFirst();
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static boolean containsTopLevelJavaFiles(final Path rootPath) {
    if (!Files.isDirectory(rootPath)) {
      return false;
    }
    try (Stream<Path> entries = Files.list(rootPath)) {
      return entries
          .sorted(STABLE_PATH_ORDER)
          .anyMatch(
              p -> {
                final Path fileName = p.getFileName();
                return fileName != null && fileName.toString().endsWith(JAVA_EXTENSION);
              });
    } catch (Exception e) {
      throw new IllegalStateException(
          MessageSource.getMessage("analysis.source_path.inspect_failed", rootPath), e);
    }
  }

  private static boolean containsNonTestJavaSources(final Path srcRoot) {
    if (!Files.isDirectory(srcRoot)) {
      return false;
    }
    final var testRoot = srcRoot.resolve("test").normalize();
    try (Stream<Path> paths = Files.walk(srcRoot)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(
              p -> {
                final Path fileName = p.getFileName();
                return fileName != null && fileName.toString().endsWith(JAVA_EXTENSION);
              })
          .anyMatch(p -> !p.normalize().startsWith(testRoot));
    } catch (Exception e) {
      throw new IllegalStateException(
          MessageSource.getMessage("analysis.source_path.inspect_failed", srcRoot), e);
    }
  }

  private static String normalizedPathKey(final Path path) {
    final String key = path.toAbsolutePath().normalize().toString();
    return key.replace('\\', '/');
  }

  private record SourceRoots(List<String> main, List<String> test) {}

  private record StrictMainSourceResult(Optional<Path> mainSource, List<String> searchedPaths) {}

  private record PathOption(String relative, Predicate<Path> condition) {

    Path resolve(final Path root) {
      return relative == null ? root : root.resolve(relative);
    }
  }

  public record SourceDirectories(Optional<Path> mainSource, Optional<Path> testSource) {

    public boolean hasMainSource() {
      return mainSource.isPresent();
    }

    public boolean hasTestSource() {
      return testSource.isPresent();
    }
  }
}
