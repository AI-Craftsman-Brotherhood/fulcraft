package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class EnvironmentLoggerTest {

  @Test
  void logStartupEnvironment_logsOnce() throws Exception {
    resetLoggedFlag();

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger targetLogger = attachAppender(appender);
    Level previousLevel = targetLogger.getLevel();
    targetLogger.setLevel(Level.DEBUG);

    try {
      EnvironmentLogger.logStartupEnvironment();
      EnvironmentLogger.logStartupEnvironment();

      long count =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(message -> message != null && message.contains("[Environment]"))
              .count();

      assertEquals(1, count);
      assertTrue(
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .anyMatch(message -> message != null && message.contains("java=")));
    } finally {
      detachAppender(targetLogger, appender, previousLevel);
    }
  }

  @Test
  void detectGradleVersion_readsVersionFromWrapperProperties(@TempDir Path tempDir)
      throws Exception {
    Path wrapper = tempDir.resolve("gradle/wrapper/gradle-wrapper.properties");
    Files.createDirectories(wrapper.getParent());
    Files.writeString(
        wrapper, "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip");

    Optional<String> version = invokeOptional("detectGradleVersion", Path.class, tempDir);

    assertEquals(Optional.of("8.7"), version);
  }

  @Test
  void detectGradleVersion_returnsEmptyWhenWrapperMissing(@TempDir Path tempDir) throws Exception {
    Optional<String> version = invokeOptional("detectGradleVersion", Path.class, tempDir);

    assertTrue(version.isEmpty());
  }

  @Test
  void detectGradleVersion_returnsEmptyWhenWrapperCannotBeRead(@TempDir Path tempDir)
      throws Exception {
    Path wrapper = tempDir.resolve("gradle/wrapper/gradle-wrapper.properties");
    Files.createDirectories(wrapper);

    Optional<String> version = invokeOptional("detectGradleVersion", Path.class, tempDir);

    assertTrue(version.isEmpty());
  }

  @Test
  void detectToolchainVersion_prefersAppBuildGradleKts(@TempDir Path tempDir) throws Exception {
    Path appKts = tempDir.resolve("app/build.gradle.kts");
    Path rootKts = tempDir.resolve("build.gradle.kts");
    Files.createDirectories(appKts.getParent());
    Files.writeString(appKts, "languageVersion.set(JavaLanguageVersion.of(21))");
    Files.writeString(rootKts, "languageVersion.set(JavaLanguageVersion.of(17))");

    Optional<String> version = invokeOptional("detectToolchainVersion", Path.class, tempDir);

    assertEquals(Optional.of("21"), version);
  }

  @Test
  void detectToolchainVersion_fallsBackToGroovyBuildWhenKtsMissing(@TempDir Path tempDir)
      throws Exception {
    Path appGradle = tempDir.resolve("app/build.gradle");
    Files.createDirectories(appGradle.getParent());
    Files.writeString(appGradle, "sourceCompatibility = '1.8'");

    Optional<String> version = invokeOptional("detectToolchainVersion", Path.class, tempDir);

    assertEquals(Optional.of("8"), version);
  }

  @Test
  void detectToolchainVersion_usesRootKtsWhenAppFilesAreMissing(@TempDir Path tempDir)
      throws Exception {
    Path rootKts = tempDir.resolve("build.gradle.kts");
    Files.writeString(rootKts, "languageVersion.set(JavaLanguageVersion.of(17))");

    Optional<String> version = invokeOptional("detectToolchainVersion", Path.class, tempDir);

    assertEquals(Optional.of("17"), version);
  }

  @Test
  void detectToolchainVersion_usesRootGroovyAsFinalFallback(@TempDir Path tempDir)
      throws Exception {
    Path rootGradle = tempDir.resolve("build.gradle");
    Files.writeString(rootGradle, "targetCompatibility = '17'");

    Optional<String> version = invokeOptional("detectToolchainVersion", Path.class, tempDir);

    assertEquals(Optional.of("17"), version);
  }

  @Test
  void extractJavaVersion_readsJavaVersionConstant(@TempDir Path tempDir) throws Exception {
    Path buildFile = tempDir.resolve("build.gradle.kts");
    Files.writeString(buildFile, "sourceCompatibility = JavaVersion.VERSION_21");

    Optional<String> version = invokeOptional("extractJavaVersion", Path.class, buildFile);

    assertEquals(Optional.of("21"), version);
  }

  @Test
  void extractJavaVersion_readsCompatibilityNotation(@TempDir Path tempDir) throws Exception {
    Path buildFile = tempDir.resolve("build.gradle");
    Files.writeString(buildFile, "sourceCompatibility = '1.8'");

    Optional<String> version = invokeOptional("extractJavaVersion", Path.class, buildFile);

    assertEquals(Optional.of("8"), version);
  }

  @Test
  void extractJavaVersion_returnsEmptyWhenBuildFileCannotBeRead(@TempDir Path tempDir)
      throws Exception {
    Path buildFile = tempDir.resolve("build.gradle.kts");
    Files.createDirectories(buildFile);

    Optional<String> version = invokeOptional("extractJavaVersion", Path.class, buildFile);

    assertTrue(version.isEmpty());
  }

  @Test
  void normalizeJavaVersion_mapsLegacyOneDotNotation() throws Exception {
    String normalized = invokeString("normalizeJavaVersion", "1", "8");

    assertEquals("8", normalized);
  }

  @Test
  void normalizeJavaVersion_keepsMajorVersionForModernNotation() throws Exception {
    String normalized = invokeString("normalizeJavaVersion", "21", "0");

    assertEquals("21", normalized);
  }

  @Test
  void valueOrUnknown_returnsUnknownForNullOrBlank() throws Exception {
    assertEquals("unknown", invokeString("valueOrUnknown", (String) null));
    assertEquals("unknown", invokeString("valueOrUnknown", "  "));
    assertEquals("21.0.2", invokeString("valueOrUnknown", "21.0.2"));
  }

  @Test
  void findGradleRoot_walksUpToSettingsFile(@TempDir Path tempDir) throws Exception {
    Path root = tempDir.resolve("workspace");
    Path nested = root.resolve("app/src/main");
    Files.createDirectories(nested);
    Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"x\"");

    Optional<Path> result = invokeOptionalPath("findGradleRoot", nested);

    assertNotNull(result);
    assertEquals(root.toAbsolutePath(), result.orElseThrow().toAbsolutePath());
  }

  @Test
  void findGradleRoot_returnsEmptyWhenNoSettingsFile(@TempDir Path tempDir) throws Exception {
    Path nested = tempDir.resolve("a/b/c");
    Files.createDirectories(nested);

    Optional<Path> result = invokeOptionalPath("findGradleRoot", nested);

    assertFalse(result.isPresent());
  }

  @Test
  void findGradleRoot_detectsGroovySettingsFile(@TempDir Path tempDir) throws Exception {
    Path root = tempDir.resolve("workspace");
    Path nested = root.resolve("app/src/test");
    Files.createDirectories(nested);
    Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'x'");

    Optional<Path> result = invokeOptionalPath("findGradleRoot", nested);

    assertTrue(result.isPresent());
    assertEquals(root.toAbsolutePath(), result.orElseThrow().toAbsolutePath());
  }

  @Test
  void findRootFromWorkingDir_returnsCurrentRepositoryRoot() throws Exception {
    Optional<Path> result = invokeOptionalPathNoArg("findRootFromWorkingDir");

    assertTrue(result.isPresent());
  }

  private void resetLoggedFlag() throws Exception {
    Field field = EnvironmentLogger.class.getDeclaredField("LOGGED");
    field.setAccessible(true);
    AtomicBoolean logged = (AtomicBoolean) field.get(null);
    logged.set(false);
  }

  private Logger attachAppender(ListAppender<ILoggingEvent> appender) {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger targetLogger = context.getLogger("utgenerator");
    appender.setContext(context);
    appender.start();
    targetLogger.addAppender(appender);
    return targetLogger;
  }

  private void detachAppender(
      Logger targetLogger, ListAppender<ILoggingEvent> appender, Level previousLevel) {
    targetLogger.detachAppender(appender);
    targetLogger.setLevel(previousLevel);
    appender.stop();
  }

  private Optional<String> invokeOptional(String methodName, Class<?> paramType, Object arg)
      throws Exception {
    Method method = EnvironmentLogger.class.getDeclaredMethod(methodName, paramType);
    method.setAccessible(true);
    return (Optional<String>) method.invoke(null, arg);
  }

  private Optional<Path> invokeOptionalPath(String methodName, Path arg) throws Exception {
    Method method = EnvironmentLogger.class.getDeclaredMethod(methodName, Path.class);
    method.setAccessible(true);
    return (Optional<Path>) method.invoke(null, arg);
  }

  private Optional<Path> invokeOptionalPathNoArg(String methodName) throws Exception {
    Method method = EnvironmentLogger.class.getDeclaredMethod(methodName);
    method.setAccessible(true);
    return (Optional<Path>) method.invoke(null);
  }

  private String invokeString(String methodName, String arg) throws Exception {
    Method method = EnvironmentLogger.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, arg);
  }

  private String invokeString(String methodName, String arg1, String arg2) throws Exception {
    Method method =
        EnvironmentLogger.class.getDeclaredMethod(methodName, String.class, String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, arg1, arg2);
  }
}
