package com.craftsmanbro.fulcraft.ui.tui.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommandResult}. */
class CommandResultTest {

  @Test
  void shouldCreateSuccessWithMultipleLines() {
    CommandResult result = CommandResult.success(List.of("Line 1", "Line 2"));

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).containsExactly("Line 1", "Line 2");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void shouldCreateSuccessWithSingleLine() {
    CommandResult result = CommandResult.success("Single line");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).containsExactly("Single line");
    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void shouldCreateError() {
    CommandResult result = CommandResult.error("Something went wrong");

    assertThat(result.success()).isFalse();
    assertThat(result.outputLines()).isEmpty();
    assertThat(result.errorMessage()).isEqualTo("Something went wrong");
  }

  @Test
  void shouldDefaultErrorMessageWhenMissing() {
    CommandResult result = new CommandResult(false, List.of(), null);

    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command_result.unknown_error"));
  }

  @Test
  void shouldClearErrorMessageWhenSuccess() {
    CommandResult result = new CommandResult(true, List.of("Ok"), "Should be cleared");

    assertThat(result.errorMessage()).isNull();
  }

  @Test
  void shouldBeImmutable() {
    List<String> lines = new java.util.ArrayList<>(List.of("Line 1"));
    CommandResult result = new CommandResult(true, lines, null);

    lines.add("Line 2");

    assertThat(result.outputLines()).containsExactly("Line 1");
  }

  @Test
  void shouldRejectNullOutputLines() {
    assertThatThrownBy(() -> new CommandResult(true, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("outputLines");
  }

  @Test
  void shouldExposeUnmodifiableOutputLines() {
    CommandResult result = new CommandResult(true, List.of("Line 1"), null);

    assertThatThrownBy(() -> result.outputLines().add("Line 2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldDefaultErrorMessageWhenFactoryReceivesNull() {
    CommandResult result = CommandResult.error(null);

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command_result.unknown_error"));
  }

  @Test
  void shouldDefaultErrorMessageWhenFactoryReceivesBlank() {
    CommandResult result = CommandResult.error("   ");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command_result.unknown_error"));
  }
}
