package com.craftsmanbro.fulcraft.ui.tui.session;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.ui.tui.UiLogger;
import com.craftsmanbro.fulcraft.ui.tui.plan.Plan;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * Manages session persistence to the .ful/sessions/ directory.
 *
 * <p>The SessionStore provides:
 *
 * <ul>
 *   <li>Session creation with collision-safe time-based IDs
 *   <li>Metadata persistence (session.json)
 *   <li>Transcript append-only logging (transcript.jsonl)
 *   <li>Plan persistence (plan.json)
 *   <li>Run log persistence (run.log)
 *   <li>Session listing and loading for resume
 * </ul>
 *
 * <p>File structure:
 *
 * <pre>
 * .ful/sessions/
 *   └── 20260102_113419_123/
 *       ├── session.json      (metadata)
 *       ├── transcript.jsonl  (conversation log)
 *       ├── plan.json         (latest plan)
 *       ├── run.log           (execution log)
 *       └── overrides.yaml    (session overrides)
 * </pre>
 */
public class SessionStore {

  private static final String SESSIONS_DIR = ".ful/sessions";

  private static final String SESSION_FILE = "session.json";

  private static final String TRANSCRIPT_FILE = "transcript.jsonl";

  private static final String PLAN_FILE = "plan.json";

  private static final String RUN_LOG_FILE = "run.log";

  private static final String OVERRIDES_FILE = "overrides.yaml";

  private static final int MAX_SESSION_ID_RETRIES = 1024;

  private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private static final Pattern SESSION_ID_PATTERN = Pattern.compile("\\d{8}_\\d{6}_\\d{3}");

  private final ObjectMapper objectMapper;

  private final Path baseDir;

  private Path sessionDir;

  private SessionMetadata metadata;

  private BufferedWriter transcriptWriter;

  private BufferedWriter runLogWriter;

  /** Creates a SessionStore with the default .ful/sessions base directory. */
  public SessionStore() {
    this(Path.of(SESSIONS_DIR));
  }

  /**
   * Creates a SessionStore with a custom base directory.
   *
   * @param baseDir base directory for sessions
   */
  public SessionStore(final Path baseDir) {
    this.baseDir = baseDir;
    this.objectMapper = createObjectMapper();
  }

  private static ObjectMapper createObjectMapper() {
    return new ObjectMapper().rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
  }

  // ========== Session Lifecycle ==========
  /**
   * Creates a new session with a unique ID.
   *
   * @param projectRoot the project root directory
   * @return the session ID
   * @throws IOException if session creation fails
   */
  public String createSession(final String projectRoot) throws IOException {
    closeWriters();
    sessionDir = createUniqueSessionDirectory();
    final Path sessionDirName = sessionDir.getFileName();
    if (sessionDirName == null) {
      throw new IOException(msg("tui.session.resolve_dir_name_failed", sessionDir));
    }
    final String id = sessionDirName.toString();
    metadata = new SessionMetadata(id, projectRoot);
    saveMetadata();
    openSessionWriters();
    UiLogger.info(msg("tui.session.created", id));
    return id;
  }

  /**
   * Loads an existing session by ID.
   *
   * @param sessionId the session ID to load
   * @return Optional with metadata if found
   * @throws IOException if loading fails
   */
  public Optional<SessionMetadata> loadSession(final String sessionId) throws IOException {
    final String normalizedSessionId = sessionId == null ? null : sessionId.trim();
    if (!isValidSessionId(normalizedSessionId)) {
      return Optional.empty();
    }
    final Optional<Path> resolvedDir = resolveSessionDirectory(normalizedSessionId);
    if (resolvedDir.isEmpty()) {
      return Optional.empty();
    }
    final Path dir = resolvedDir.get();
    if (!Files.isDirectory(dir)) {
      return Optional.empty();
    }
    final Path metadataFile = dir.resolve(SESSION_FILE);
    if (!Files.exists(metadataFile)) {
      return Optional.empty();
    }
    final SessionMetadata loaded;
    try {
      loaded = objectMapper.readValue(metadataFile.toFile(), SessionMetadata.class);
    } catch (tools.jackson.core.JacksonException e) {
      UiLogger.warn(msg("tui.session.metadata_read_failed", metadataFile, e.getMessage()));
      return Optional.empty();
    }
    closeWriters();
    sessionDir = dir;
    metadata = loaded;
    // Mark as resumed
    metadata.setStatus(SessionMetadata.SessionStatus.RESUMED);
    saveMetadata();
    openSessionWriters();
    UiLogger.info(msg("tui.session.loaded", normalizedSessionId));
    return Optional.of(metadata);
  }

  /**
   * Validates whether a session ID follows the canonical timestamp format.
   *
   * @param sessionId candidate session ID
   * @return true when the ID format is yyyyMMdd_HHmmss_ddd
   */
  public static boolean isValidSessionId(final String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return false;
    }
    return SESSION_ID_PATTERN.matcher(sessionId).matches();
  }

  private Optional<Path> resolveSessionDirectory(final String sessionId) {
    final Path normalizedBaseDir = baseDir.toAbsolutePath().normalize();
    final Path resolved = normalizedBaseDir.resolve(sessionId).normalize();
    if (!resolved.startsWith(normalizedBaseDir)) {
      UiLogger.warn(msg("tui.session.path_rejected", sessionId));
      return Optional.empty();
    }
    return Optional.of(resolved);
  }

  /**
   * Closes the current session.
   *
   * @param completed true if session completed normally
   */
  public void closeSession(final boolean completed) {
    if (metadata != null) {
      metadata.setStatus(
          completed
              ? SessionMetadata.SessionStatus.COMPLETED
              : SessionMetadata.SessionStatus.INTERRUPTED);
      try {
        saveMetadata();
      } catch (IOException e) {
        UiLogger.warn(msg("tui.session.metadata_save_failed", e.getMessage()));
      }
    }
    closeWriters();
    UiLogger.info(
        msg("tui.session.closed", metadata != null ? metadata.getId() : msg("report.value.na")));
  }

  private Path createUniqueSessionDirectory() throws IOException {
    Files.createDirectories(baseDir);
    IOException lastException = null;
    for (int attempt = 0; attempt < MAX_SESSION_ID_RETRIES; attempt++) {
      final String id = nextSessionId();
      final Path candidate = baseDir.resolve(id);
      try {
        return Files.createDirectory(candidate);
      } catch (FileAlreadyExistsException e) {
        lastException = e;
      }
    }
    throw new IOException(
        msg("tui.session.id_allocation_failed", MAX_SESSION_ID_RETRIES), lastException);
  }

  private void closeWriters() {
    if (transcriptWriter != null) {
      try {
        transcriptWriter.close();
      } catch (IOException e) {
        UiLogger.warn(msg("tui.session.writer_close_transcript_failed", e.getMessage()));
      }
      transcriptWriter = null;
    }
    if (runLogWriter != null) {
      try {
        runLogWriter.close();
      } catch (IOException e) {
        UiLogger.warn(msg("tui.session.writer_close_runlog_failed", e.getMessage()));
      }
      runLogWriter = null;
    }
  }

  private void openSessionWriters() throws IOException {
    openTranscriptWriter();
    openRunLogWriter();
  }

  private Optional<Path> currentSessionFile(final String fileName) {
    if (sessionDir == null) {
      return Optional.empty();
    }
    return Optional.of(sessionDir.resolve(fileName));
  }

  private Optional<Path> existingSessionFile(final String fileName) {
    return currentSessionFile(fileName).filter(Files::exists);
  }

  // ========== Session Listing ==========
  /**
   * Lists all sessions ordered by most recent first.
   *
   * @return list of session metadata
   */
  public List<SessionMetadata> listSessions() {
    final List<SessionMetadata> sessions = new ArrayList<>();
    if (!Files.isDirectory(baseDir)) {
      return sessions;
    }
    try (Stream<Path> dirs = Files.list(baseDir)) {
      dirs.filter(Files::isDirectory)
          .sorted(Comparator.comparing(Path::getFileName).reversed())
          .forEach(
              dir -> {
                final Path metadataFile = dir.resolve(SESSION_FILE);
                if (Files.exists(metadataFile)) {
                  try {
                    final SessionMetadata meta =
                        objectMapper.readValue(metadataFile.toFile(), SessionMetadata.class);
                    sessions.add(meta);
                  } catch (tools.jackson.core.JacksonException e) {
                    UiLogger.warn(msg("tui.session.list_metadata_failed", dir));
                  }
                }
              });
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.list_failed", e.getMessage()));
    }
    return sessions;
  }

  // ========== Metadata Persistence ==========
  /**
   * Saves the current session metadata.
   *
   * @throws IOException if saving fails
   */
  public void saveMetadata() throws IOException {
    if (metadata == null) {
      return;
    }
    final Optional<Path> metadataFile = currentSessionFile(SESSION_FILE);
    if (metadataFile.isEmpty()) {
      return;
    }
    metadata.touch();
    objectMapper.writeValue(metadataFile.get().toFile(), metadata);
  }

  /**
   * Updates and saves session metadata.
   *
   * @param updater function to update metadata
   * @throws IOException if saving fails
   */
  public void updateMetadata(final java.util.function.Consumer<SessionMetadata> updater)
      throws IOException {
    if (metadata != null) {
      updater.accept(metadata);
      saveMetadata();
    }
  }

  /**
   * Returns the current session metadata.
   *
   * @return optional metadata
   */
  public Optional<SessionMetadata> getMetadata() {
    return Optional.ofNullable(metadata);
  }

  // ========== Transcript Persistence ==========
  /**
   * Appends an entry to the transcript log.
   *
   * @param entry transcript entry
   */
  public void appendTranscript(final TranscriptEntry entry) {
    if (transcriptWriter == null) {
      return;
    }
    try {
      final String json =
          objectMapper
              .writer()
              .without(SerializationFeature.INDENT_OUTPUT)
              .writeValueAsString(entry);
      transcriptWriter.write(json);
      transcriptWriter.newLine();
      transcriptWriter.flush();
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.transcript_write_failed", e.getMessage()));
    }
  }

  /**
   * Reads all transcript entries from the current session.
   *
   * @return list of transcript entries
   */
  public List<TranscriptEntry> readTranscript() {
    final List<TranscriptEntry> entries = new ArrayList<>();
    final Optional<Path> transcriptFile = existingSessionFile(TRANSCRIPT_FILE);
    if (transcriptFile.isEmpty()) {
      return entries;
    }
    final Path file = transcriptFile.get();
    try {
      final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (final String line : lines) {
        if (!line.isBlank()) {
          entries.add(objectMapper.readValue(line, TranscriptEntry.class));
        }
      }
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.transcript_read_failed", e.getMessage()));
    }
    return entries;
  }

  private void openTranscriptWriter() throws IOException {
    final Optional<Path> transcriptFile = currentSessionFile(TRANSCRIPT_FILE);
    if (transcriptFile.isPresent()) {
      transcriptWriter =
          Files.newBufferedWriter(
              transcriptFile.get(),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
    }
  }

  // ========== Plan Persistence ==========
  /**
   * Saves the current plan.
   *
   * @param plan the plan to save
   * @throws IOException if saving fails
   */
  public void savePlan(final Plan plan) throws IOException {
    if (plan == null) {
      return;
    }
    final Optional<Path> planFile = currentSessionFile(PLAN_FILE);
    if (planFile.isEmpty()) {
      return;
    }
    objectMapper.writeValue(planFile.get().toFile(), plan);
  }

  /**
   * Loads the plan from the current session.
   *
   * @return optional plan
   */
  public Optional<Plan> loadPlan() {
    final Optional<Path> planFile = existingSessionFile(PLAN_FILE);
    if (planFile.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readValue(planFile.get().toFile(), Plan.class));
    } catch (tools.jackson.core.JacksonException e) {
      UiLogger.warn(msg("tui.session.plan_load_failed", e.getMessage()));
      return Optional.empty();
    }
  }

  // ========== Run Log Persistence ==========
  /**
   * Appends a line to the run log.
   *
   * @param line log line
   */
  public void appendRunLog(final String line) {
    if (runLogWriter == null) {
      return;
    }
    try {
      runLogWriter.write("[" + Instant.now() + "] " + line);
      runLogWriter.newLine();
      runLogWriter.flush();
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.run_log_write_failed", e.getMessage()));
    }
  }

  /**
   * Reads all run log lines from the current session.
   *
   * @return list of log lines
   */
  public List<String> readRunLog() {
    final Optional<Path> runLogFile = existingSessionFile(RUN_LOG_FILE);
    if (runLogFile.isEmpty()) {
      return List.of();
    }
    try {
      return Files.readAllLines(runLogFile.get(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.run_log_read_failed", e.getMessage()));
      return List.of();
    }
  }

  private void openRunLogWriter() throws IOException {
    final Optional<Path> runLogFile = currentSessionFile(RUN_LOG_FILE);
    if (runLogFile.isPresent()) {
      runLogWriter =
          Files.newBufferedWriter(
              runLogFile.get(),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
    }
  }

  // ========== Overrides Persistence ==========
  /**
   * Saves session overrides as YAML.
   *
   * @param overrides YAML content
   * @throws IOException if saving fails
   */
  public void saveOverrides(final String overrides) throws IOException {
    final Optional<Path> overridesFile = currentSessionFile(OVERRIDES_FILE);
    if (overridesFile.isEmpty()) {
      return;
    }
    Files.writeString(overridesFile.get(), overrides, StandardCharsets.UTF_8);
  }

  /**
   * Loads session overrides.
   *
   * @return optional YAML content
   */
  public Optional<String> loadOverrides() {
    final Optional<Path> overridesFile = existingSessionFile(OVERRIDES_FILE);
    if (overridesFile.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(overridesFile.get(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      UiLogger.warn(msg("tui.session.overrides_load_failed", e.getMessage()));
      return Optional.empty();
    }
  }

  // ========== Utility ==========
  /**
   * Generates the next session ID candidate.
   *
   * <p>Visible for testing to force collision scenarios.
   *
   * @return session ID candidate
   */
  protected String nextSessionId() {
    return generateSessionId();
  }

  /**
   * Generates a session ID candidate based on timestamp.
   *
   * @return session ID in format: yyyyMMdd_HHmmss_xxx
   */
  private static String generateSessionId() {
    final LocalDateTime now = LocalDateTime.now();
    final String timestamp = now.format(ID_FORMAT);
    final String suffix =
        String.format("%03d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000));
    return timestamp + "_" + suffix;
  }

  /**
   * Returns the current session ID.
   *
   * @return optional session ID
   */
  public Optional<String> getCurrentSessionId() {
    return metadata != null ? Optional.of(metadata.getId()) : Optional.empty();
  }

  /**
   * Returns the session directory path for the current session.
   *
   * @return optional session directory
   */
  public Optional<Path> getSessionDir() {
    return Optional.ofNullable(sessionDir);
  }

  /**
   * Returns the base sessions directory.
   *
   * @return base directory path
   */
  public Path getBaseDir() {
    return baseDir;
  }

  /**
   * Formats a session for display in list.
   *
   * @param session session metadata
   * @return formatted display string
   */
  public static String formatSessionForDisplay(final SessionMetadata session) {
    final DateTimeFormatter displayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    final String na = msg("report.value.na");
    final String startTimeDisplay;
    if (session.getStartTime() == null) {
      startTimeDisplay = na;
    } else {
      final LocalDateTime startTime =
          LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault());
      startTimeDisplay = startTime.format(displayFormat);
    }
    final String stateDisplay =
        session.getCurrentState() != null ? session.getCurrentState().name() : na;
    final String statusDisplay = session.getStatus() != null ? session.getStatus().name() : na;
    return msg(
        "resume.list.format",
        session.getId(),
        startTimeDisplay,
        stateDisplay,
        statusDisplay,
        truncatePath(session.getProjectRoot(), 30));
  }

  private static String truncatePath(final String path, final int maxLen) {
    if (path == null) {
      return msg("report.value.na");
    }
    if (path.length() <= maxLen) {
      return path;
    }
    return "..." + path.substring(path.length() - maxLen + 3);
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
