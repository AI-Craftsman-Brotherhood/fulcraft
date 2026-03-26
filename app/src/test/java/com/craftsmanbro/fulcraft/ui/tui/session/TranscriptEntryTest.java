package com.craftsmanbro.fulcraft.ui.tui.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.session.TranscriptEntry.EntryType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link TranscriptEntry}. */
class TranscriptEntryTest {

  @Test
  @DisplayName("Default constructor should create empty entry")
  void defaultConstructor() {
    TranscriptEntry entry = new TranscriptEntry();
    assertThat(entry.getType()).isNull();
    assertThat(entry.getContent()).isNull();
    assertThat(entry.getTimestamp()).isNull();
  }

  @Test
  @DisplayName("Constructor with arguments should set fields and timestamp")
  void constructorWithArgs() {
    TranscriptEntry entry = new TranscriptEntry(EntryType.USER_INPUT, "test content");
    assertThat(entry.getType()).isEqualTo(EntryType.USER_INPUT);
    assertThat(entry.getContent()).isEqualTo("test content");
    assertThat(entry.getTimestamp()).isNotNull();
  }

  @Test
  @DisplayName("userInput factory should create correct entry")
  void userInputFactory() {
    TranscriptEntry entry = TranscriptEntry.userInput("input");
    assertThat(entry.getType()).isEqualTo(EntryType.USER_INPUT);
    assertThat(entry.getContent()).isEqualTo("input");
  }

  @Test
  @DisplayName("systemResponse factory should create correct entry")
  void systemResponseFactory() {
    TranscriptEntry entry = TranscriptEntry.systemResponse("response");
    assertThat(entry.getType()).isEqualTo(EntryType.SYSTEM_RESPONSE);
    assertThat(entry.getContent()).isEqualTo("response");
  }

  @Test
  @DisplayName("stateChange factory should create correct entry with metadata")
  void stateChangeFactory() {
    TranscriptEntry entry = TranscriptEntry.stateChange("HOME", "CHAT");
    assertThat(entry.getType()).isEqualTo(EntryType.STATE_CHANGE);
    assertThat(entry.getContent()).contains("HOME -> CHAT");
    assertThat(entry.getFromState()).isEqualTo("HOME");
    assertThat(entry.getToState()).isEqualTo("CHAT");
  }

  @Test
  @DisplayName("stateChange should use fallback labels for null/blank states")
  void stateChangeFactoryWithNullOrBlankStates() {
    TranscriptEntry entry = TranscriptEntry.stateChange(null, " ");
    assertThat(entry.getType()).isEqualTo(EntryType.STATE_CHANGE);
    assertThat(entry.getContent()).contains("(none) -> (none)");
    assertThat(entry.getFromState()).isNull();
    assertThat(entry.getToState()).isEqualTo(" ");
  }

  @Test
  @DisplayName("command factory should create correct entry with metadata")
  void commandFactory() {
    TranscriptEntry entry = TranscriptEntry.command("/help", "Show help");
    assertThat(entry.getType()).isEqualTo(EntryType.COMMAND);
    assertThat(entry.getContent()).isEqualTo("/help");
    assertThat(entry.getMetadata()).isEqualTo("Show help");
  }

  @Test
  @DisplayName("error factory should create correct entry")
  void errorFactory() {
    TranscriptEntry entry = TranscriptEntry.error("Failed");
    assertThat(entry.getType()).isEqualTo(EntryType.ERROR);
    assertThat(entry.getContent()).isEqualTo("Failed");
  }

  @Test
  @DisplayName("executionLog factory should create correct entry")
  void executionLogFactory() {
    TranscriptEntry entry = TranscriptEntry.executionLog("Running...");
    assertThat(entry.getType()).isEqualTo(EntryType.EXECUTION_LOG);
    assertThat(entry.getContent()).isEqualTo("Running...");
  }

  @Test
  @DisplayName("userDecision factory should create correct entry")
  void userDecisionFactory() {
    TranscriptEntry entry = TranscriptEntry.userDecision("Retry");
    assertThat(entry.getType()).isEqualTo(EntryType.USER_DECISION);
    assertThat(entry.getContent()).isEqualTo("Retry");
  }

  @Test
  @DisplayName("toString should format correctly")
  void testToString() {
    TranscriptEntry entry = new TranscriptEntry(EntryType.USER_INPUT, "Hello");
    // Just verify it contains key information
    assertThat(entry.toString()).contains("USER_INPUT", "Hello");
  }

  @Test
  @DisplayName("getters and setters should round-trip all mutable fields")
  void gettersAndSettersRoundTrip() {
    Instant now = Instant.now();
    TranscriptEntry entry = new TranscriptEntry();
    entry.setTimestamp(now);
    entry.setType(EntryType.COMMAND);
    entry.setContent("/run");
    entry.setFromState("HOME");
    entry.setToState("EXECUTION_RUNNING");
    entry.setMetadata("ok");

    assertThat(entry.getTimestamp()).isEqualTo(now);
    assertThat(entry.getType()).isEqualTo(EntryType.COMMAND);
    assertThat(entry.getContent()).isEqualTo("/run");
    assertThat(entry.getFromState()).isEqualTo("HOME");
    assertThat(entry.getToState()).isEqualTo("EXECUTION_RUNNING");
    assertThat(entry.getMetadata()).isEqualTo("ok");
  }
}
