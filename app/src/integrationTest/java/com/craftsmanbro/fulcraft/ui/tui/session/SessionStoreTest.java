package com.craftsmanbro.fulcraft.ui.tui.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link SessionStore}. */
class SessionStoreTest {

  @TempDir Path tempDir;

  private SessionStore sessionStore;

  @BeforeEach
  void setUp() {
    sessionStore = new SessionStore(tempDir);
  }

  @Test
  @DisplayName("createSession should create directory and metadata")
  void createSession() throws IOException {
    String projectRoot = "/path/to/project";
    String sessionId = sessionStore.createSession(projectRoot);

    assertThat(sessionId).matches("\\d{8}_\\d{6}_\\d{3}");
    assertThat(sessionStore.getCurrentSessionId()).contains(sessionId);
    assertThat(sessionStore.getSessionDir()).isPresent();
    assertThat(Files.exists(sessionStore.getSessionDir().get())).isTrue();
    assertThat(Files.exists(sessionStore.getSessionDir().get().resolve("session.json"))).isTrue();

    // Verify metadata content
    Optional<SessionMetadata> metadata = sessionStore.getMetadata();
    assertThat(metadata).isPresent();
    assertThat(metadata.get().getId()).isEqualTo(sessionId);
    assertThat(metadata.get().getProjectRoot()).isEqualTo(projectRoot);
  }

  @Test
  @DisplayName("createSession should retry when generated ID already exists")
  void createSessionRetriesOnCollision() throws IOException {
    String collidingId = "20260101_010101_111";
    String uniqueId = "20260101_010101_222";
    Files.createDirectories(tempDir.resolve(collidingId));

    SessionStore storeWithCollision =
        new SessionStore(tempDir) {
          private int callCount = 0;

          @Override
          protected String nextSessionId() {
            callCount++;
            return callCount == 1 ? collidingId : uniqueId;
          }
        };

    String sessionId = storeWithCollision.createSession("/test");

    assertThat(sessionId).isEqualTo(uniqueId);
    assertThat(storeWithCollision.getSessionDir()).contains(tempDir.resolve(uniqueId));
    assertThat(Files.exists(tempDir.resolve(collidingId).resolve("session.json"))).isFalse();
    assertThat(Files.exists(tempDir.resolve(uniqueId).resolve("session.json"))).isTrue();
  }

  @Test
  @DisplayName("loadSession should load existing session")
  void loadSession() throws IOException {
    String sessionId = sessionStore.createSession("/path/to/project");
    sessionStore.closeSession(false);

    // Create new store and load
    SessionStore newStore = new SessionStore(tempDir);
    Optional<SessionMetadata> loaded = newStore.loadSession(sessionId);

    assertThat(loaded).isPresent();
    assertThat(loaded.get().getId()).isEqualTo(sessionId);
    assertThat(loaded.get().getStatus()).isEqualTo(SessionMetadata.SessionStatus.RESUMED);
    assertThat(newStore.getCurrentSessionId()).contains(sessionId);
  }

  @Test
  @DisplayName("loadSession should return empty for non-existent ID")
  void loadSessionReturnsEmpty() throws IOException {
    Optional<SessionMetadata> loaded = sessionStore.loadSession("20260101_010101_999");
    assertThat(loaded).isEmpty();
  }

  @Test
  @DisplayName("loadSession should return empty for null/empty ID")
  void loadSessionNullOrEmpty() throws IOException {
    assertThat(sessionStore.loadSession(null)).isEmpty();
    assertThat(sessionStore.loadSession("")).isEmpty();
  }

  @Test
  @DisplayName("loadSession should return empty for blank ID")
  void loadSessionBlankId() throws IOException {
    assertThat(sessionStore.loadSession("   ")).isEmpty();
  }

  @Test
  @DisplayName("loadSession should reject session IDs outside canonical format")
  void loadSessionRejectsInvalidSessionIdFormat() throws IOException {
    assertThat(sessionStore.loadSession("../20260101_010101_111")).isEmpty();
    assertThat(sessionStore.loadSession("20260101_010101_11A")).isEmpty();
  }

  @Test
  @DisplayName("isValidSessionId should validate canonical format")
  void validatesSessionIdFormat() {
    assertThat(SessionStore.isValidSessionId("20260101_010101_111")).isTrue();
    assertThat(SessionStore.isValidSessionId("20260101_010101_11A")).isFalse();
    assertThat(SessionStore.isValidSessionId("../20260101_010101_111")).isFalse();
  }

  @Test
  @DisplayName("loadSession should return empty when metadata file is missing")
  void loadSessionWithoutMetadataFile() throws IOException {
    Files.createDirectories(tempDir.resolve("20260101_010101_111"));
    assertThat(sessionStore.loadSession("20260101_010101_111")).isEmpty();
  }

  @Test
  @DisplayName("loadSession should return empty when metadata JSON is invalid")
  void loadSessionWithInvalidMetadataJson() throws IOException {
    Path sessionDir = tempDir.resolve("20260101_010101_222");
    Files.createDirectories(sessionDir);
    Files.writeString(sessionDir.resolve("session.json"), "{invalid", StandardCharsets.UTF_8);

    assertThat(sessionStore.loadSession("20260101_010101_222")).isEmpty();
  }

  @Test
  @DisplayName("transcript should be persisted and readable")
  void transcriptPersistence() throws IOException {
    String sessionId = sessionStore.createSession("/test");

    sessionStore.appendTranscript(TranscriptEntry.userInput("Hello"));
    sessionStore.appendTranscript(TranscriptEntry.systemResponse("Hi there"));
    sessionStore.closeSession(false);

    // Re-load to verify persistence
    SessionStore loader = new SessionStore(tempDir);
    loader.loadSession(sessionId);
    List<TranscriptEntry> transcript = loader.readTranscript();

    assertThat(transcript).hasSize(2);
    assertThat(transcript.get(0).getType()).isEqualTo(TranscriptEntry.EntryType.USER_INPUT);
    assertThat(transcript.get(0).getContent()).isEqualTo("Hello");
    assertThat(transcript.get(1).getType()).isEqualTo(TranscriptEntry.EntryType.SYSTEM_RESPONSE);
    assertThat(transcript.get(1).getContent()).isEqualTo("Hi there");
  }

  @Test
  @DisplayName("plan should be persisted and readable")
  void planPersistence() throws IOException {
    String sessionId = sessionStore.createSession("/test");

    Plan plan = new Plan("My Goal", List.of("Step 1", "Step 2"), "Success", List.of("None"));
    sessionStore.savePlan(plan);

    // Re-load
    SessionStore loader = new SessionStore(tempDir);
    loader.loadSession(sessionId);
    Optional<Plan> loadedPlan = loader.loadPlan();

    assertThat(loadedPlan).isPresent();
    assertThat(loadedPlan.get().goal()).isEqualTo("My Goal");
    assertThat(loadedPlan.get().steps()).containsExactly("Step 1", "Step 2");
  }

  @Test
  @DisplayName("run log should be persisted and readable")
  void runLogPersistence() throws IOException {
    String sessionId = sessionStore.createSession("/test");

    sessionStore.appendRunLog("Log line 1");
    sessionStore.appendRunLog("Log line 2");
    sessionStore.closeSession(false);

    // Re-load
    SessionStore loader = new SessionStore(tempDir);
    loader.loadSession(sessionId);
    List<String> log = loader.readRunLog();

    assertThat(log).hasSize(2);
    assertThat(log.get(0)).contains("Log line 1");
    assertThat(log.get(1)).contains("Log line 2");
  }

  @Test
  @DisplayName("overrides should be persisted and readable")
  void overridesPersistence() throws IOException {
    String sessionId = sessionStore.createSession("/test");

    String overrides = "key: value\nfoo: bar";
    sessionStore.saveOverrides(overrides);

    // Re-load
    SessionStore loader = new SessionStore(tempDir);
    loader.loadSession(sessionId);
    Optional<String> loadedOverrides = loader.loadOverrides();

    assertThat(loadedOverrides).isPresent();
    assertThat(loadedOverrides.get()).isEqualTo(overrides);
  }

  @Test
  @DisplayName("listSessions should return sessions ordered by recency")
  void listSessions() throws IOException {
    String s1 = sessionStore.createSession("/p1");
    String s2 = sessionStore.createSession("/p2");

    List<SessionMetadata> sessions = sessionStore.listSessions();

    assertThat(sessions).hasSize(2);
    List<String> expectedOrder =
        List.of(s1, s2).stream().sorted(Comparator.reverseOrder()).toList();
    assertThat(sessions)
        .extracting(SessionMetadata::getId)
        .containsExactlyElementsOf(expectedOrder);
  }

  @Test
  @DisplayName("listSessions should skip directories with invalid metadata")
  void listSessionsSkipsInvalidMetadata() throws IOException {
    String validSessionId = sessionStore.createSession("/valid");

    Path invalidSessionDir = tempDir.resolve("20260101_010101_999");
    Files.createDirectories(invalidSessionDir);
    Files.writeString(
        invalidSessionDir.resolve("session.json"), "{ broken json", StandardCharsets.UTF_8);

    List<SessionMetadata> sessions = sessionStore.listSessions();

    assertThat(sessions).extracting(SessionMetadata::getId).contains(validSessionId);
  }

  @Test
  @DisplayName("listSessions should return empty when baseDir is not a directory")
  void listSessionsWhenBaseDirIsAFile() throws IOException {
    Path fileAsBaseDir = tempDir.resolve("sessions.txt");
    Files.writeString(fileAsBaseDir, "not a dir", StandardCharsets.UTF_8);
    SessionStore fileBackedStore = new SessionStore(fileAsBaseDir);

    assertThat(fileBackedStore.listSessions()).isEmpty();
  }

  @Test
  @DisplayName("updateMetadata should modify and save")
  void updateMetadata() throws IOException {
    sessionStore.createSession("/test");

    sessionStore.updateMetadata(
        m -> {
          m.setUserInput("New Input");
          m.setCurrentState(State.CHAT_INPUT);
        });

    Optional<SessionMetadata> m = sessionStore.getMetadata();
    assertThat(m).isPresent();
    assertThat(m.get().getUserInput()).isEqualTo("New Input");
    assertThat(m.get().getCurrentState()).isEqualTo(State.CHAT_INPUT);

    // Verify it is saved
    SessionStore loader = new SessionStore(tempDir);
    loader.loadSession(m.get().getId());
    assertThat(loader.getMetadata().get().getUserInput()).isEqualTo("New Input");
  }

  @Test
  @DisplayName("saveMetadata and updateMetadata should no-op when session is not active")
  void metadataPersistenceWithoutActiveSession() {
    assertThatCode(() -> sessionStore.saveMetadata()).doesNotThrowAnyException();
    assertThatCode(() -> sessionStore.updateMetadata(m -> m.setUserInput("ignored")))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("readTranscript should return empty when session is not active")
  void readTranscriptWithoutActiveSession() {
    assertThat(sessionStore.readTranscript()).isEmpty();
  }

  @Test
  @DisplayName("readTranscript should ignore blank lines")
  void readTranscriptIgnoresBlankLines() throws IOException {
    sessionStore.createSession("/test");
    sessionStore.appendTranscript(TranscriptEntry.userInput("hello"));

    Path transcriptFile = sessionStore.getSessionDir().orElseThrow().resolve("transcript.jsonl");
    Files.writeString(
        transcriptFile, System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);

    List<TranscriptEntry> entries = sessionStore.readTranscript();
    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().getContent()).isEqualTo("hello");
  }

  @Test
  @DisplayName("loadPlan should return empty when plan is missing")
  void loadPlanWhenPlanIsMissing() throws IOException {
    sessionStore.createSession("/test");
    assertThat(sessionStore.loadPlan()).isEmpty();
  }

  @Test
  @DisplayName("loadPlan should return empty when plan JSON is invalid")
  void loadPlanWithInvalidJson() throws IOException {
    sessionStore.createSession("/test");
    Path planFile = sessionStore.getSessionDir().orElseThrow().resolve("plan.json");
    Files.writeString(planFile, "{invalid", StandardCharsets.UTF_8);

    assertThat(sessionStore.loadPlan()).isEmpty();
  }

  @Test
  @DisplayName("run log operations should no-op when session is not active")
  void runLogOperationsWithoutActiveSession() {
    assertThatCode(() -> sessionStore.appendRunLog("line")).doesNotThrowAnyException();
    assertThat(sessionStore.readRunLog()).isEmpty();
  }

  @Test
  @DisplayName("overrides operations should no-op when session is not active")
  void overridesOperationsWithoutActiveSession() {
    assertThatCode(() -> sessionStore.saveOverrides("key: value")).doesNotThrowAnyException();
    assertThat(sessionStore.loadOverrides()).isEmpty();
  }

  @Test
  @DisplayName("closeSession should persist COMPLETED status")
  void closeSessionPersistsCompletedStatus() throws IOException {
    String sessionId = sessionStore.createSession("/test");
    sessionStore.closeSession(true);

    SessionStore loader = new SessionStore(tempDir);
    Optional<SessionMetadata> completedMetadata =
        loader.listSessions().stream().filter(meta -> sessionId.equals(meta.getId())).findFirst();

    assertThat(completedMetadata).isPresent();
    assertThat(completedMetadata.orElseThrow().getStatus())
        .isEqualTo(SessionMetadata.SessionStatus.COMPLETED);
  }

  @Test
  @DisplayName("formatSessionForDisplay should format correctly")
  void formatSessionForDisplay() {
    String projectRoot = "/very/long/path/to/my/project/is/actually/quite/long/indeed";
    SessionMetadata metadata = new SessionMetadata("id-123", projectRoot);
    metadata.setCurrentState(State.SUMMARY);
    metadata.setStatus(SessionMetadata.SessionStatus.COMPLETED);

    String display = SessionStore.formatSessionForDisplay(metadata);
    String expectedSuffix = projectRoot.substring(projectRoot.length() - 27);

    assertThat(display).contains("id-123");
    assertThat(display).contains("SUMMARY");
    assertThat(display).contains("COMPLETED");
    assertThat(display).contains("..." + expectedSuffix);
  }

  @Test
  @DisplayName("formatSessionForDisplay should use N/A for null fields")
  void formatSessionForDisplayWithNullFields() {
    SessionMetadata metadata = new SessionMetadata();
    metadata.setId("id-null");
    metadata.setStartTime(null);
    metadata.setCurrentState(null);
    metadata.setStatus(null);
    metadata.setProjectRoot(null);

    String display = SessionStore.formatSessionForDisplay(metadata);

    assertThat(display).contains("[id-null]");
    assertThat(display).contains("State: N/A");
    assertThat(display).contains("Status: N/A");
    assertThat(display).contains("Project: N/A");
  }

  @Test
  @DisplayName("accessors should return empty before session creation")
  void accessorsBeforeSessionCreation() {
    assertThat(sessionStore.getCurrentSessionId()).isEmpty();
    assertThat(sessionStore.getSessionDir()).isEmpty();
    assertThat(sessionStore.getBaseDir()).isEqualTo(tempDir);
  }
}
