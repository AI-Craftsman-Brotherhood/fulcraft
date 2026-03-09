package com.craftsmanbro.fulcraft.infrastructure.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.infrastructure.version.impl.DefaultVersionInfoResolver;
import com.craftsmanbro.fulcraft.infrastructure.version.model.VersionInfo;
import com.google.common.hash.Hashing;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class VersionInfoTest {

  @TempDir Path tempDir;
  private static final DefaultVersionInfoResolver resolver = new DefaultVersionInfoResolver();

  @Test
  void of_withLockfileComputesHashesAndCombinedHash() {
    String configContent = "project:\n  id: demo\n";
    String lockContent = "gradle lock data";

    VersionInfo info = resolver.fromContent(configContent, lockContent);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256(lockContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void of_withoutLockfileComputesHashesAndCombinedHash() {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";

    VersionInfo info = resolver.fromContent(configContent);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void of_withNullConfigUsesEmptyHashAndNoLockHash() {
    VersionInfo info = resolver.fromContent((String) null);

    String expectedConfigHash = sha256("");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromProject_readsConfigAndPrefersGradleLockfile() throws Exception {
    String configContent = "{ \"project\": { \"id\": \"demo\" } }\n";
    Files.writeString(tempDir.resolve("config.json"), configContent);

    String gradleLockContent = "gradle lock data";
    Files.writeString(tempDir.resolve("gradle.lockfile"), gradleLockContent);

    Files.createDirectories(tempDir.resolve("gradle/dependency-locks"));
    Files.writeString(
        tempDir.resolve("gradle/dependency-locks/other.lockfile"), "dependency lock data");
    Files.writeString(tempDir.resolve("pom.xml.lock"), "pom lock data");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256(gradleLockContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_usesSecondPreferredLockfilePattern_whenFirstIsMissing() throws Exception {
    String configContent = "{\"schema_version\":\"1\"}";
    Files.writeString(tempDir.resolve("config.json"), configContent);

    String buildSrcLockContent = "buildSrc lock data";
    Files.createDirectories(tempDir.resolve("buildSrc"));
    Files.writeString(tempDir.resolve("buildSrc/gradle.lockfile"), buildSrcLockContent);

    Files.createDirectories(tempDir.resolve(".gradle/caches/modules-2"));
    Files.writeString(
        tempDir.resolve(".gradle/caches/modules-2/modules-2.lock"), "modules lock data");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256(buildSrcLockContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_usesDependencyLockfiles_whenPreferredLockfilesAreMissing() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);

    Path dependencyLocksDir = tempDir.resolve("gradle/dependency-locks");
    Files.createDirectories(dependencyLocksDir);
    Files.writeString(dependencyLocksDir.resolve("z.lockfile"), "z-content");
    Files.writeString(dependencyLocksDir.resolve("a.lockfile"), "a-content");
    Files.writeString(dependencyLocksDir.resolve("blank.lockfile"), "   ");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedCombinedLockContent = "file:a.lockfile\na-content\nfile:z.lockfile\nz-content\n";
    String expectedLockHash = sha256(expectedCombinedLockContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_usesPomLockfile_whenNoGradleLockfileFound() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);
    Files.writeString(tempDir.resolve("pom.xml.lock"), "pom lock data");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256("pom lock data");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_readsAbsoluteConfigPath() throws Exception {
    Path configPath = tempDir.resolve("absolute-config.json");
    String configContent = "{\"AppName\":\"ful\"}";
    Files.writeString(configPath, configContent);

    VersionInfo info = resolver.fromProject(tempDir.resolve("unused"), configPath, false);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromProject_usesEmptyConfigHash_whenConfigFileIsMissingOrNull() {
    VersionInfo missingConfigInfo =
        resolver.fromProject(tempDir, Path.of("missing-config.json"), false);
    VersionInfo nullConfigPathInfo = resolver.fromProject(tempDir, null, false);

    String expectedConfigHash = sha256("");

    assertEquals(expectedConfigHash, missingConfigInfo.getConfigHash());
    assertEquals(expectedConfigHash, nullConfigPathInfo.getConfigHash());
    assertNull(missingConfigInfo.getLockfileHash());
    assertNull(nullConfigPathInfo.getLockfileHash());
  }

  @Test
  void fromProject_hashesBlankPomLockfile_whenIncludeLockfileIsTrue() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);
    Files.writeString(tempDir.resolve("pom.xml.lock"), "   ");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256("   ");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_doesNotIncludeLockfileHash_whenIncludeLockfileFalse() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);
    Files.writeString(tempDir.resolve("gradle.lockfile"), "gradle lock data");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), false);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromProject_includeLockfileTrue_keepsLockHashNullWhenNoLockfilesExist() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromProject_skipsBlankPreferredLockfileAndFallsBackToDependencyLockfiles() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);
    Files.writeString(tempDir.resolve("gradle.lockfile"), "   ");

    Path dependencyLocksDir = tempDir.resolve("gradle/dependency-locks");
    Files.createDirectories(dependencyLocksDir);
    Files.createDirectories(dependencyLocksDir.resolve("ignored.lockfile"));
    Files.writeString(dependencyLocksDir.resolve("a.lockfile"), "a-content");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);
    String expectedLockHash = sha256("file:a.lockfile\na-content\n");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromProject_keepsLockHashNullWhenDependencyLockDirectoryIsEmpty() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);
    Files.createDirectories(tempDir.resolve("gradle/dependency-locks"));

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromProject_keepsLockHashNullWhenDependencyLockfilesAreBlankOnly() throws Exception {
    String configContent = "{\"project\":{\"id\":\"demo\"}}";
    Files.writeString(tempDir.resolve("config.json"), configContent);

    Path dependencyLocksDir = tempDir.resolve("gradle/dependency-locks");
    Files.createDirectories(dependencyLocksDir);
    Files.writeString(dependencyLocksDir.resolve("first.lockfile"), "   ");
    Files.writeString(dependencyLocksDir.resolve("second.lockfile"), "");

    VersionInfo info = resolver.fromProject(tempDir, Path.of("config.json"), true);

    String expectedConfigHash = sha256(configContent);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromConfig_serializesConfigAndUsesPomLockfile() throws Exception {
    Files.writeString(tempDir.resolve("pom.xml.lock"), "pom lock data");

    Config config = new Config();
    Config.ProjectConfig project = new Config.ProjectConfig();
    project.setId("demo");
    config.setProject(project);

    VersionInfo info = resolver.fromConfig(config, tempDir, true);

    ObjectMapper mapper = JsonMapperFactory.create();
    String configJson = mapper.writeValueAsString(config);
    String expectedConfigHash = sha256(configJson);
    String expectedLockHash = sha256("pom lock data");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertEquals(expectedLockHash, info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, expectedLockHash),
        info.getCombinedHash());
  }

  @Test
  void fromConfig_ignoresBlankLockfileContent() throws Exception {
    Files.writeString(tempDir.resolve("pom.xml.lock"), "   ");

    Config config = new Config();
    config.setAppName("ful");

    VersionInfo info = resolver.fromConfig(config, tempDir, true);

    ObjectMapper mapper = JsonMapperFactory.create();
    String configJson = mapper.writeValueAsString(config);
    String expectedConfigHash = sha256(configJson);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromConfig_withNullConfigUsesEmptyHash() {
    VersionInfo info = resolver.fromConfig(null, tempDir, false);

    String expectedConfigHash = sha256("");

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void fromConfig_includeLockfileTrue_keepsLockHashNullWhenNoLockfilesExist() throws Exception {
    Config config = new Config();
    config.setAppName("ful");

    VersionInfo info = resolver.fromConfig(config, tempDir, true);

    ObjectMapper mapper = JsonMapperFactory.create();
    String configJson = mapper.writeValueAsString(config);
    String expectedConfigHash = sha256(configJson);

    assertEquals(expectedConfigHash, info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertEquals(
        combinedHash(info.getApplicationVersion(), expectedConfigHash, null),
        info.getCombinedHash());
  }

  @Test
  void loadApplicationVersion_usesFallbackWhenVersionKeyIsMissing() throws Exception {
    withVersionPropertiesContent(
        "build.timestamp=1\n",
        () -> {
          String version = invokeLoadApplicationVersion();
          assertEquals(expectedVersionWithoutUsableVersionProperty(), version);
        });
  }

  @Test
  void loadApplicationVersion_usesFallbackWhenVersionValueIsBlank() throws Exception {
    withVersionPropertiesContent(
        "version=   \n",
        () -> {
          String version = invokeLoadApplicationVersion();
          assertEquals(expectedVersionWithoutUsableVersionProperty(), version);
        });
  }

  @Test
  void loadApplicationVersion_usesFallbackWhenVersionPropertiesIsMissing() throws Exception {
    withVersionPropertiesContent(
        null,
        () -> {
          String version = invokeLoadApplicationVersion();
          assertEquals(expectedVersionWithoutUsableVersionProperty(), version);
        });
  }

  @Test
  void empty_returnsUnknownAndNoVersion() {
    VersionInfo info = resolver.empty();

    assertEquals("unknown", info.getApplicationVersion());
    assertEquals("", info.getConfigHash());
    assertNull(info.getLockfileHash());
    assertFalse(info.hasVersion());
    assertEquals(
        combinedHash(info.getApplicationVersion(), info.getConfigHash(), null),
        info.getCombinedHash());
  }

  @Test
  void toString_truncatesHashesAndShowsLockfileSectionConditionally() {
    VersionInfo withLock = resolver.fromContent("config", "lock");
    VersionInfo withoutLock = resolver.fromContent("config");

    String withLockText = withLock.toString();
    String withoutLockText = withoutLock.toString();

    String configHashPrefix = withLock.getConfigHash().substring(0, 12) + "...";
    String lockHashPrefix = withLock.getLockfileHash().substring(0, 12) + "...";
    String combinedHashPrefix = withLock.getCombinedHash().substring(0, 12) + "...";

    assertTrue(withLockText.contains("configHash='" + configHashPrefix + "'"));
    assertTrue(withLockText.contains("lockHash='" + lockHashPrefix + "'"));
    assertTrue(withLockText.contains("combinedHash='" + combinedHashPrefix + "'"));

    assertTrue(withoutLockText.contains("configHash='"));
    assertTrue(withoutLockText.contains("combinedHash='"));
    assertFalse(withoutLockText.contains("lockHash='"));
  }

  @Test
  void of_populatesApplicationVersionAndHasVersionConsistently() {
    VersionInfo info = resolver.fromContent("{}");

    assertNotNull(info.getApplicationVersion());
    assertEquals(!"unknown".equals(info.getApplicationVersion()), info.hasVersion());
  }

  @Test
  void truncateHash_returnsInputForNullOrShortValues() throws Exception {
    assertNull(invokeTruncateHash(null));
    assertEquals("short", invokeTruncateHash("short"));
    assertEquals("123456789012", invokeTruncateHash("123456789012"));
    assertEquals("123456789012...", invokeTruncateHash("1234567890123"));
  }

  private static String sha256(String value) {
    return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
  }

  private static String invokeLoadApplicationVersion() throws Exception {
    Method method = DefaultVersionInfoResolver.class.getDeclaredMethod("loadApplicationVersion");
    method.setAccessible(true);
    return (String) method.invoke(null);
  }

  private static String invokeTruncateHash(String hash) throws Exception {
    Method method = VersionInfo.class.getDeclaredMethod("truncateHash", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, hash);
  }

  private static String expectedVersionWithoutUsableVersionProperty() {
    Package pkg = DefaultVersionInfoResolver.class.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null) {
      return pkg.getImplementationVersion();
    }
    String envVersion = System.getenv("FUL_VERSION");
    if (envVersion != null && !envVersion.isBlank()) {
      return envVersion.trim();
    }
    return "unknown";
  }

  private static void withVersionPropertiesContent(String replacement, ThrowingRunnable action)
      throws Exception {
    Path versionPropertiesPath = resolveVersionPropertiesPath();
    byte[] originalBytes = Files.readAllBytes(versionPropertiesPath);
    try {
      if (replacement == null) {
        Files.deleteIfExists(versionPropertiesPath);
      } else {
        Files.writeString(
            versionPropertiesPath,
            replacement,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE);
      }
      action.run();
    } finally {
      Files.write(
          versionPropertiesPath,
          originalBytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE);
    }
  }

  private static Path resolveVersionPropertiesPath() throws Exception {
    URL resource = DefaultVersionInfoResolver.class.getResource("/version.properties");
    assertNotNull(resource);
    assertEquals("file", resource.getProtocol());
    return Path.of(resource.toURI());
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static String combinedHash(String appVersion, String configHash, String lockHash) {
    StringBuilder sb = new StringBuilder();
    sb.append("app:").append(appVersion);
    sb.append("|cfg:").append(configHash);
    if (lockHash != null) {
      sb.append("|lock:").append(lockHash);
    }
    return sha256(sb.toString());
  }
}
