package com.craftsmanbro.fulcraft.ui.tui.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommandDispatcher}. */
class CommandDispatcherTest {

  private CommandDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new CommandDispatcher();
  }

  @Test
  void isCommandShouldReturnTrueForSlashPrefix() {
    assertThat(dispatcher.isCommand("/help")).isTrue();
    assertThat(dispatcher.isCommand("/status")).isTrue();
    assertThat(dispatcher.isCommand("/anything")).isTrue();
    assertThat(dispatcher.isCommand("   /help")).isTrue();
  }

  @Test
  void isCommandShouldReturnFalseForNonSlashPrefix() {
    assertThat(dispatcher.isCommand("help")).isFalse();
    assertThat(dispatcher.isCommand("")).isFalse();
    assertThat(dispatcher.isCommand(null)).isFalse();
  }

  @Test
  void shouldDispatchHelpCommand() {
    CommandResult result = dispatcher.dispatch("/help");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).isNotEmpty();
    assertThat(String.join("\n", result.outputLines())).contains("/help");
    assertThat(String.join("\n", result.outputLines())).contains("/model");
    assertThat(String.join("\n", result.outputLines())).contains("/config get");
  }

  @Test
  void shouldDispatchHelpCommandWithLeadingWhitespace() {
    CommandResult result = dispatcher.dispatch("   /help");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).isNotEmpty();
    assertThat(String.join("\n", result.outputLines())).contains("/help");
  }

  @Test
  void shouldDispatchSlashAsHelpCommand() {
    CommandResult result = dispatcher.dispatch("/");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).isNotEmpty();
    assertThat(String.join("\n", result.outputLines())).contains("/help");
  }

  @Test
  void shouldDispatchStatusCommand() {
    CommandResult result = dispatcher.dispatch("/status");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).isNotEmpty();
    assertThat(String.join("\n", result.outputLines()))
        .contains(MessageSource.getMessage("tui.command.status.header"));
  }

  @Test
  void shouldBeCaseInsensitive() {
    assertThat(dispatcher.dispatch("/HELP").success()).isTrue();
    assertThat(dispatcher.dispatch("/Help").success()).isTrue();
    assertThat(dispatcher.dispatch("/STATUS").success()).isTrue();
  }

  @Test
  void shouldShowHelpForUnknownSlashCommand() {
    CommandResult result = dispatcher.dispatch("/unknown");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).isNotEmpty();
    assertThat(String.join("\n", result.outputLines())).contains("/help");
  }

  @Test
  void shouldReturnErrorForNonCommand() {
    CommandResult result = dispatcher.dispatch("not a command");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command.not_a_command", "not a command"));
  }

  @Test
  void shouldRegisterCustomCommand() {
    dispatcher.register("/custom", args -> CommandResult.success("Custom output"));

    CommandResult result = dispatcher.dispatch("/custom");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).containsExactly("Custom output");
  }

  @Test
  void shouldPassArgumentsToHandler() {
    dispatcher.register("/echo", args -> CommandResult.success("Echo: " + args));

    CommandResult result = dispatcher.dispatch("/echo hello world");

    assertThat(result.success()).isTrue();
    assertThat(result.outputLines()).containsExactly("Echo: hello world");
  }

  @Test
  void shouldPreserveInnerWhitespaceInArguments() {
    dispatcher.register("/echo", args -> CommandResult.success(args));

    CommandResult result = dispatcher.dispatch("/echo    hello   world");

    assertThat(result.outputLines()).containsExactly("hello   world");
  }

  @Test
  void shouldNormalizeRegisteredCommandToLowercase() {
    dispatcher.register("  /Custom  ", args -> CommandResult.success("ok"));

    assertThat(dispatcher.getRegisteredCommands()).contains("/custom");
    assertThat(dispatcher.dispatch("/CUSTOM").success()).isTrue();
  }

  @Test
  void shouldRejectInvalidCommandRegistration() {
    assertThatThrownBy(() -> dispatcher.register("noSlash", args -> null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(MessageSource.getMessage("tui.command.error.must_start_with_slash"));

    assertThatThrownBy(() -> dispatcher.register(null, args -> null))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> dispatcher.register("/custom", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            MessageSource.getMessage("tui.command.error.handler_required", "/custom"));
  }

  @Test
  void shouldListRegisteredCommands() {
    assertThat(dispatcher.getRegisteredCommands()).containsExactly("/help", "/", "/status");
  }

  @Test
  void shouldUseCustomHelpLinesForHelpAliasAndUnknownCommand() {
    List<String> customLines = List.of("custom help", "/custom command");
    dispatcher.setHelpLines(customLines);

    assertThat(dispatcher.dispatch("/help").outputLines()).containsExactlyElementsOf(customLines);
    assertThat(dispatcher.dispatch("/").outputLines()).containsExactlyElementsOf(customLines);
    assertThat(dispatcher.dispatch("/unknown").outputLines())
        .containsExactlyElementsOf(customLines);
  }

  @Test
  void shouldResetHelpLinesToDefaultWhenNullOrEmpty() {
    dispatcher.setHelpLines(List.of("custom only"));
    dispatcher.setHelpLines(null);

    assertThat(dispatcher.getHelpLines()).contains(MessageSource.getMessage("tut.help.help"));

    dispatcher.setHelpLines(List.of("custom only"));
    dispatcher.setHelpLines(List.of());

    assertThat(dispatcher.getHelpLines()).contains(MessageSource.getMessage("tut.help.quit"));
  }

  @Test
  void shouldKeepHelpLinesImmutableFromCallerAndGetter() {
    List<String> mutable = new ArrayList<>(List.of("line 1"));
    dispatcher.setHelpLines(mutable);
    mutable.add("line 2");

    assertThat(dispatcher.getHelpLines()).containsExactly("line 1");
    assertThatThrownBy(() -> dispatcher.getHelpLines().add("line 3"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldReturnErrorWhenHandlerThrows() {
    dispatcher.register(
        "/explode",
        args -> {
          throw new IllegalStateException("boom");
        });

    CommandResult result = dispatcher.dispatch("/explode");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command.error.execution_failed", "/explode"));
  }

  @Test
  void shouldReturnErrorWhenHandlerReturnsNull() {
    dispatcher.register("/broken", args -> null);

    CommandResult result = dispatcher.dispatch("/broken");

    assertThat(result.success()).isFalse();
    assertThat(result.errorMessage())
        .isEqualTo(MessageSource.getMessage("tui.command.error.execution_failed", "/broken"));
  }
}
