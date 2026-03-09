package com.craftsmanbro.fulcraft.infrastructure.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.vcs.impl.GitService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitServiceTest {

  @TempDir Path tempDir;

  @Test
  void constructor_throwsWhenProjectRootNull() {
    assertThrows(NullPointerException.class, () -> new GitService(null));
  }

  @Test
  void getCommitCount_returnsZero_whenPathNullOrBlank() throws Exception {
    GitService service = new GitService(tempDir);

    assertEquals(0, service.getCommitCount(null));
    assertEquals(0, service.getCommitCount(""));
    assertEquals(0, service.getCommitCount("   "));

    Map<String, Integer> cache = commitCountCacheOf(service);
    assertTrue(cache.isEmpty(), "Null/blank path should not be cached");
  }

  @Test
  void getCommitCount_returnsCommitCount_forTrackedFile() throws Exception {
    Assumptions.assumeTrue(isGitAvailable(), "git is required for this test");

    Path repo = initRepo(tempDir.resolve("repo"));
    commitFile(repo, "a.txt", "v1", "add a");
    commitFile(repo, "a.txt", "v2", "update a");
    commitFile(repo, "b.txt", "v1", "add b");

    GitService service = new GitService(repo);
    assertEquals(2, service.getCommitCount("a.txt"));
    assertEquals(1, service.getCommitCount("b.txt"));
  }

  @Test
  void getCommitCount_usesCache_whenHistoryAdvances() throws Exception {
    Assumptions.assumeTrue(isGitAvailable(), "git is required for this test");

    Path repo = initRepo(tempDir.resolve("repo"));
    commitFile(repo, "a.txt", "v1", "add a");
    commitFile(repo, "a.txt", "v2", "update a");

    GitService service = new GitService(repo);
    assertEquals(2, service.getCommitCount("a.txt"));

    commitFile(repo, "a.txt", "v3", "update a again");
    assertEquals(3, gitCommitCount(repo, "a.txt"));

    assertEquals(2, service.getCommitCount("a.txt"), "Should return cached count");
  }

  @Test
  void getCommitCount_cachesFailureAsZero_whenProjectRootMissing() throws Exception {
    Path missingRoot = tempDir.resolve("missing-root");
    GitService service = new GitService(missingRoot);

    assertEquals(0, service.getCommitCount("a.txt"));

    Map<String, Integer> cache = commitCountCacheOf(service);
    assertEquals(0, cache.get("a.txt"));
  }

  @Test
  void getCommitCount_resolvesSourceRootRelativePath() throws Exception {
    Assumptions.assumeTrue(isGitAvailable(), "git is required for this test");

    Path repo = initRepo(tempDir.resolve("repo"));
    commitFile(repo, "src/main/java/com/example/A.java", "class A {}", "add A");

    GitService service = new GitService(repo);
    service.setSourceRootPaths(List.of("src/main/java"));

    assertEquals(1, service.getCommitCount("com/example/A.java"));
  }

  @Test
  void setSourceRootPaths_normalizesAndSkipsBlankEntries() throws Exception {
    GitService service = new GitService(tempDir);

    service.setSourceRootPaths(
        Arrays.asList(null, "", " ", "src/main/../main/java", "./src/test/java"));

    assertIterableEquals(
        List.of(Path.of("src/main/java"), Path.of("src/test/java")), sourceRootPathsOf(service));
  }

  @Test
  void setSourceRootPaths_clearsPaths_whenNullOrEmptyProvided() throws Exception {
    GitService service = new GitService(tempDir);
    service.setSourceRootPaths(List.of("src/main/java"));

    service.setSourceRootPaths(null);
    assertTrue(sourceRootPathsOf(service).isEmpty());

    service.setSourceRootPaths(List.of("src/main/java"));
    service.setSourceRootPaths(List.of());
    assertTrue(sourceRootPathsOf(service).isEmpty());
  }

  @Test
  void getCommitCount_relativizesAbsolutePath_insideProjectRoot() {
    Path projectRoot = tempDir.resolve("project");
    RecordingGitService service =
        new RecordingGitService(projectRoot, FakeProcess.completed("7\n", 0));
    Path absolutePath = projectRoot.resolve("src/main/java/com/example/A.java");

    assertEquals(7, service.getCommitCount(absolutePath.toString()));
    assertIterableEquals(List.of("src/main/java/com/example/A.java"), service.startedPaths());
  }

  @Test
  void getCommitCount_triesSourceRootPath_thenOriginalPathWhenFirstCandidateFails()
      throws Exception {
    Path projectRoot = tempDir.resolve("project");
    Path sourceFile = projectRoot.resolve("src/main/java/com/example/A.java");
    Files.createDirectories(sourceFile.getParent());
    Files.writeString(sourceFile, "class A {}", StandardCharsets.UTF_8);

    RecordingGitService service =
        new RecordingGitService(
            projectRoot, FakeProcess.completed("", 1), FakeProcess.completed("3\n", 0));
    service.setSourceRootPaths(List.of("src/main/java"));

    assertEquals(3, service.getCommitCount("com/example/A.java"));
    assertIterableEquals(
        List.of("src/main/java/com/example/A.java", "com/example/A.java"), service.startedPaths());
  }

  @Test
  void getCommitCount_returnsZero_whenGitProcessTimesOut() {
    FakeProcess timeoutProcess = FakeProcess.timeout();
    RecordingGitService service = new RecordingGitService(tempDir, timeoutProcess);

    assertEquals(0, service.getCommitCount("A.java"));
    assertTrue(timeoutProcess.wasDestroyedForcibly());
  }

  @Test
  void getCommitCount_returnsZero_whenGitOutputIsNonNumeric() {
    RecordingGitService service =
        new RecordingGitService(tempDir, FakeProcess.completed("not-a-number\n", 0));

    assertEquals(0, service.getCommitCount("A.java"));
  }

  @Test
  void getCommitCount_usesRawPath_whenInputCannotBeNormalized() {
    String invalidPath = "broken\u0000path";
    RecordingGitService service = new RecordingGitService(tempDir, FakeProcess.completed("4\n", 0));

    assertEquals(4, service.getCommitCount(invalidPath));
    assertIterableEquals(List.of(invalidPath), service.startedPaths());
  }

  @Test
  void getCommitCount_returnsZeroAndInterruptsThread_whenWaitIsInterrupted() {
    RecordingGitService service =
        new RecordingGitService(tempDir, FakeProcess.interruptedDuringWait());

    try {
      assertEquals(0, service.getCommitCount("A.java"));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private static boolean isGitAvailable() {
    try {
      Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
      return process.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static Path initRepo(Path repoDir) throws Exception {
    Files.createDirectories(repoDir);
    runGit(repoDir, "init");
    runGit(repoDir, "config", "user.email", "support@craftsmann-bro.com");
    runGit(repoDir, "config", "user.name", "Test User");
    runGit(repoDir, "config", "commit.gpgsign", "false");
    return repoDir;
  }

  private static void commitFile(Path repoDir, String filePath, String content, String message)
      throws Exception {
    Path file = repoDir.resolve(filePath);
    Files.createDirectories(file.getParent() == null ? repoDir : file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
    runGit(repoDir, "add", filePath);
    runGit(repoDir, "commit", "-m", message);
  }

  private static int gitCommitCount(Path repoDir, String filePath) throws Exception {
    String out = runGit(repoDir, "rev-list", "--count", "HEAD", "--", filePath);
    return Integer.parseInt(out.trim());
  }

  private static String runGit(Path repoDir, String... args) throws Exception {
    ProcessBuilder pb = new ProcessBuilder();
    pb.command(concat("git", args));
    pb.directory(repoDir.toFile());
    pb.redirectErrorStream(true);
    Process process = pb.start();
    String output = readAll(process.getInputStream());
    int exit = process.waitFor();
    if (exit != 0) {
      throw new IllegalStateException(
          "git command failed: " + String.join(" ", pb.command()) + "\n" + output);
    }
    return output;
  }

  private static String readAll(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    is.transferTo(baos);
    return baos.toString(StandardCharsets.UTF_8);
  }

  private static String[] concat(String first, String... rest) {
    String[] all = new String[rest.length + 1];
    all[0] = first;
    System.arraycopy(rest, 0, all, 1, rest.length);
    return all;
  }

  private static Map<String, Integer> commitCountCacheOf(GitService service) throws Exception {
    Field f = GitService.class.getDeclaredField("commitCountCache");
    f.setAccessible(true);
    return (Map<String, Integer>) f.get(service);
  }

  private static List<Path> sourceRootPathsOf(GitService service) throws Exception {
    Field f = GitService.class.getDeclaredField("sourceRootPaths");
    f.setAccessible(true);
    AtomicReference<List<Path>> ref = (AtomicReference<List<Path>>) f.get(service);
    return ref.get();
  }

  private static final class RecordingGitService extends GitService {
    private final ArrayDeque<Process> processes;
    private final List<String> startedPaths = new ArrayList<>();

    RecordingGitService(Path projectRoot, Process... processes) {
      super(projectRoot);
      this.processes = new ArrayDeque<>(List.of(processes));
    }

    @Override
    protected Process startGitProcess(String filePath) throws IOException {
      startedPaths.add(filePath);
      if (processes.isEmpty()) {
        throw new IOException("No fake process configured");
      }
      return processes.removeFirst();
    }

    List<String> startedPaths() {
      return List.copyOf(startedPaths);
    }
  }

  private static final class FakeProcess extends Process {
    private final byte[] stdout;
    private final boolean waitReturns;
    private final int exitCode;
    private final boolean interruptOnWait;
    private boolean forciblyDestroyed;

    private FakeProcess(String output, boolean waitReturns, int exitCode, boolean interruptOnWait) {
      this.stdout = output.getBytes(StandardCharsets.UTF_8);
      this.waitReturns = waitReturns;
      this.exitCode = exitCode;
      this.interruptOnWait = interruptOnWait;
    }

    static FakeProcess completed(String output, int exitCode) {
      return new FakeProcess(output, true, exitCode, false);
    }

    static FakeProcess timeout() {
      return new FakeProcess("", false, 0, false);
    }

    static FakeProcess interruptedDuringWait() {
      return new FakeProcess("", true, 0, true);
    }

    boolean wasDestroyedForcibly() {
      return forciblyDestroyed;
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(stdout);
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      if (interruptOnWait) {
        throw new InterruptedException("simulated interrupt");
      }
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      if (interruptOnWait) {
        throw new InterruptedException("simulated interrupt");
      }
      return waitReturns;
    }

    @Override
    public int exitValue() {
      if (!waitReturns) {
        throw new IllegalThreadStateException("still running");
      }
      return exitCode;
    }

    @Override
    public void destroy() {
      forciblyDestroyed = true;
    }

    @Override
    public Process destroyForcibly() {
      forciblyDestroyed = true;
      return this;
    }

    @Override
    public boolean isAlive() {
      return !waitReturns && !forciblyDestroyed;
    }
  }
}
