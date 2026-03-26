package com.craftsmanbro.fulcraft.ui.tui.session;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Session metadata stored in session.json.
 *
 * <p>Contains basic information about the session for persistence and resume:
 *
 * <ul>
 *   <li>Session ID (unique identifier)
 *   <li>Start time
 *   <li>Last update time
 *   <li>Project root directory
 *   <li>Current TUI state
 *   <li>Selected conflict policy
 *   <li>Session status (ACTIVE, COMPLETED, INTERRUPTED, RESUMED)
 *   <li>Latest user input captured for the session
 *   <li>Session metadata format version
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionMetadata {

  private static final String CURRENT_VERSION = "1.0";

  /** Session lifecycle status. */
  public enum SessionStatus {

    /** Session is currently active. */
    ACTIVE,
    /** Session completed normally. */
    COMPLETED,
    /** Session was interrupted (crash, exit, etc). */
    INTERRUPTED,
    /** Session was resumed from a previous session. */
    RESUMED
  }

  @JsonProperty("id")
  private String id;

  @JsonProperty("startTime")
  private Instant startTime;

  @JsonProperty("lastUpdateTime")
  private Instant lastUpdateTime;

  @JsonProperty("projectRoot")
  private String projectRoot;

  @JsonProperty("currentState")
  private State currentState;

  @JsonProperty("conflictPolicy")
  private ConflictPolicy conflictPolicy;

  @JsonProperty("status")
  private SessionStatus status;

  @JsonProperty("userInput")
  private String userInput;

  @JsonProperty("version")
  private String version;

  /** Default constructor for Jackson. */
  public SessionMetadata() {}

  /**
   * Creates new session metadata with the given ID.
   *
   * @param id session identifier
   * @param projectRoot project root path
   */
  public SessionMetadata(final String id, final String projectRoot) {
    this.id = id;
    this.projectRoot = projectRoot;
    this.startTime = Instant.now();
    this.lastUpdateTime = this.startTime;
    this.currentState = State.HOME;
    this.status = SessionStatus.ACTIVE;
    this.version = CURRENT_VERSION;
  }

  // ========== Getters/Setters ==========
  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public void setStartTime(final Instant startTime) {
    this.startTime = startTime;
  }

  public Instant getLastUpdateTime() {
    return lastUpdateTime;
  }

  public void setLastUpdateTime(final Instant lastUpdateTime) {
    this.lastUpdateTime = lastUpdateTime;
  }

  public String getProjectRoot() {
    return projectRoot;
  }

  public void setProjectRoot(final String projectRoot) {
    this.projectRoot = projectRoot;
  }

  public State getCurrentState() {
    return currentState;
  }

  public void setCurrentState(final State currentState) {
    this.currentState = currentState;
    refreshLastUpdateTime();
  }

  public ConflictPolicy getConflictPolicy() {
    return conflictPolicy;
  }

  public void setConflictPolicy(final ConflictPolicy conflictPolicy) {
    this.conflictPolicy = conflictPolicy;
    refreshLastUpdateTime();
  }

  public SessionStatus getStatus() {
    return status;
  }

  public void setStatus(final SessionStatus status) {
    this.status = status;
    refreshLastUpdateTime();
  }

  public String getUserInput() {
    return userInput;
  }

  public void setUserInput(final String userInput) {
    this.userInput = userInput;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  /** Updates last update time to now. */
  public void touch() {
    refreshLastUpdateTime();
  }

  private void refreshLastUpdateTime() {
    this.lastUpdateTime = Instant.now();
  }

  @Override
  public String toString() {
    return String.format(
        "SessionMetadata{id='%s', state=%s, status=%s, startTime=%s}",
        id, currentState, status, startTime);
  }
}
