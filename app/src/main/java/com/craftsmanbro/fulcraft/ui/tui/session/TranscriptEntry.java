package com.craftsmanbro.fulcraft.ui.tui.session;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * A single entry in the session transcript (transcript.jsonl).
 *
 * <p>Each entry represents a user interaction or system event in chronological order. The
 * transcript is stored as JSONL (one JSON object per line) for append-only writing and crash
 * safety.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptEntry {

  /** The type of transcript entry. */
  public enum EntryType {

    /** User input from chat. */
    USER_INPUT,
    /** System response or generated plan. */
    SYSTEM_RESPONSE,
    /** State transition event. */
    STATE_CHANGE,
    /** Command execution (slash command). */
    COMMAND,
    /** Error or exception. */
    ERROR,
    /** Execution log line. */
    EXECUTION_LOG,
    /** User decision (policy choice, issue handling). */
    USER_DECISION
  }

  @JsonProperty("timestamp")
  private Instant timestamp;

  @JsonProperty("type")
  private EntryType type;

  @JsonProperty("content")
  private String content;

  @JsonProperty("fromState")
  private String fromState;

  @JsonProperty("toState")
  private String toState;

  @JsonProperty("metadata")
  private String metadata;

  /** Default constructor for Jackson. */
  public TranscriptEntry() {}

  /**
   * Creates a new transcript entry.
   *
   * @param type entry type
   * @param content entry content
   */
  public TranscriptEntry(final EntryType type, final String content) {
    this.timestamp = Instant.now();
    this.type = type;
    this.content = content;
  }

  // ========== Factory methods ==========
  /**
   * Creates a user input entry.
   *
   * @param input the user's input text
   * @return new transcript entry
   */
  public static TranscriptEntry userInput(final String input) {
    return new TranscriptEntry(EntryType.USER_INPUT, input);
  }

  /**
   * Creates a system response entry.
   *
   * @param response the system response
   * @return new transcript entry
   */
  public static TranscriptEntry systemResponse(final String response) {
    return new TranscriptEntry(EntryType.SYSTEM_RESPONSE, response);
  }

  /**
   * Creates a state change entry.
   *
   * @param fromState previous state name (nullable for initial state)
   * @param toState new state name
   * @return new transcript entry
   */
  public static TranscriptEntry stateChange(final String fromState, final String toState) {
    final String previousStateLabel = formatStateLabel(fromState);
    final String nextStateLabel = formatStateLabel(toState);
    final TranscriptEntry stateChangeEntry =
        new TranscriptEntry(
            EntryType.STATE_CHANGE,
            MessageSource.getMessage(
                "tui.transcript.state_change", previousStateLabel, nextStateLabel));
    stateChangeEntry.fromState = fromState;
    stateChangeEntry.toState = toState;
    return stateChangeEntry;
  }

  /**
   * Creates a command entry.
   *
   * @param command command text
   * @param detail command status or detail message
   * @return new transcript entry
   */
  public static TranscriptEntry command(final String command, final String detail) {
    final TranscriptEntry entry = new TranscriptEntry(EntryType.COMMAND, command);
    entry.metadata = detail;
    return entry;
  }

  /**
   * Creates an error entry.
   *
   * @param error error message
   * @return new transcript entry
   */
  public static TranscriptEntry error(final String error) {
    return new TranscriptEntry(EntryType.ERROR, error);
  }

  /**
   * Creates an execution log entry.
   *
   * @param logLine log line content
   * @return new transcript entry
   */
  public static TranscriptEntry executionLog(final String logLine) {
    return new TranscriptEntry(EntryType.EXECUTION_LOG, logLine);
  }

  /**
   * Creates a user decision entry.
   *
   * @param decision description of the decision
   * @return new transcript entry
   */
  public static TranscriptEntry userDecision(final String decision) {
    return new TranscriptEntry(EntryType.USER_DECISION, decision);
  }

  // ========== Getters/Setters ==========
  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final Instant timestamp) {
    this.timestamp = timestamp;
  }

  public EntryType getType() {
    return type;
  }

  public void setType(final EntryType type) {
    this.type = type;
  }

  public String getContent() {
    return content;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public String getFromState() {
    return fromState;
  }

  public void setFromState(final String fromState) {
    this.fromState = fromState;
  }

  public String getToState() {
    return toState;
  }

  public void setToState(final String toState) {
    this.toState = toState;
  }

  public String getMetadata() {
    return metadata;
  }

  public void setMetadata(final String metadata) {
    this.metadata = metadata;
  }

  private static String formatStateLabel(final String state) {
    if (state == null || state.isBlank()) {
      return MessageSource.getMessage("tui.transcript.none");
    }
    return state;
  }

  @Override
  public String toString() {
    return String.format("[%s] %s: %s", timestamp, type, content);
  }
}
