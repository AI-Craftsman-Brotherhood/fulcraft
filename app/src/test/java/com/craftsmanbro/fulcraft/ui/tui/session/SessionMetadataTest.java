package com.craftsmanbro.fulcraft.ui.tui.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictPolicy;
import com.craftsmanbro.fulcraft.ui.tui.state.State;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link SessionMetadata}. */
class SessionMetadataTest {

  @Test
  @DisplayName("Default constructor should create instance")
  void defaultConstructor() {
    SessionMetadata metadata = new SessionMetadata();
    assertThat(metadata).isNotNull();
  }

  @Test
  @DisplayName("Constructor with ID should initialize default values")
  void parameterizedConstructor() {
    SessionMetadata metadata = new SessionMetadata("session-123", "/root");

    assertThat(metadata.getId()).isEqualTo("session-123");
    assertThat(metadata.getProjectRoot()).isEqualTo("/root");
    assertThat(metadata.getStartTime()).isNotNull();
    assertThat(metadata.getLastUpdateTime()).isEqualTo(metadata.getStartTime());
    assertThat(metadata.getCurrentState()).isEqualTo(State.HOME);
    assertThat(metadata.getStatus()).isEqualTo(SessionMetadata.SessionStatus.ACTIVE);
    assertThat(metadata.getVersion()).isEqualTo("1.0");
  }

  @Test
  @DisplayName("touch should update lastUpdateTime")
  void touchShouldUpdateTimestamp() {
    SessionMetadata metadata = new SessionMetadata("id", "root");
    metadata.setLastUpdateTime(Instant.EPOCH);

    metadata.touch();

    assertThat(metadata.getLastUpdateTime()).isAfter(Instant.EPOCH);
  }

  @Test
  @DisplayName("setCurrentState should update state and timestamp")
  void setCurrentStateShouldUpdateTimestamp() {
    SessionMetadata metadata = new SessionMetadata("id", "root");
    metadata.setLastUpdateTime(Instant.EPOCH);

    metadata.setCurrentState(State.CHAT_INPUT);

    assertThat(metadata.getCurrentState()).isEqualTo(State.CHAT_INPUT);
    assertThat(metadata.getLastUpdateTime()).isAfter(Instant.EPOCH);
  }

  @Test
  @DisplayName("setConflictPolicy should update policy and timestamp")
  void setConflictPolicyShouldUpdateTimestamp() {
    SessionMetadata metadata = new SessionMetadata("id", "root");
    metadata.setLastUpdateTime(Instant.EPOCH);

    metadata.setConflictPolicy(ConflictPolicy.SKIP);

    assertThat(metadata.getConflictPolicy()).isEqualTo(ConflictPolicy.SKIP);
    assertThat(metadata.getLastUpdateTime()).isAfter(Instant.EPOCH);
  }

  @Test
  @DisplayName("setStatus should update status and timestamp")
  void setStatusShouldUpdateTimestamp() {
    SessionMetadata metadata = new SessionMetadata("id", "root");
    metadata.setLastUpdateTime(Instant.EPOCH);

    metadata.setStatus(SessionMetadata.SessionStatus.COMPLETED);

    assertThat(metadata.getStatus()).isEqualTo(SessionMetadata.SessionStatus.COMPLETED);
    assertThat(metadata.getLastUpdateTime()).isAfter(Instant.EPOCH);
  }

  @Test
  @DisplayName("toString should contain key info")
  void testToString() {
    SessionMetadata metadata = new SessionMetadata("test-id", "root");
    String str = metadata.toString();

    assertThat(str).contains("test-id", "HOME", "ACTIVE");
  }

  @Test
  @DisplayName("Getters and Setters should work")
  void gettersAndSetters() {
    SessionMetadata metadata = new SessionMetadata();
    metadata.setId("id");
    metadata.setUserInput("input");
    metadata.setVersion("2.0");
    Instant now = Instant.now();
    metadata.setStartTime(now);
    metadata.setLastUpdateTime(now);

    assertThat(metadata.getId()).isEqualTo("id");
    assertThat(metadata.getUserInput()).isEqualTo("input");
    assertThat(metadata.getVersion()).isEqualTo("2.0");
    assertThat(metadata.getStartTime()).isEqualTo(now);
    assertThat(metadata.getLastUpdateTime()).isEqualTo(now);
  }

  @Test
  @DisplayName("setUserInput and setVersion should not change lastUpdateTime")
  void setUserInputAndVersionShouldNotUpdateTimestamp() {
    SessionMetadata metadata = new SessionMetadata("id", "root");
    metadata.setLastUpdateTime(Instant.EPOCH);

    metadata.setUserInput("new input");
    metadata.setVersion("2.0");

    assertThat(metadata.getUserInput()).isEqualTo("new input");
    assertThat(metadata.getVersion()).isEqualTo("2.0");
    assertThat(metadata.getLastUpdateTime()).isEqualTo(Instant.EPOCH);
  }
}
