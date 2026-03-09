package com.craftsmanbro.fulcraft.ui.tui.command;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Dispatches and executes slash commands.
 *
 * <p>Slash commands start with '/' and provide quick access to utility functions. Commands are
 * registered with handlers that produce a {@link CommandResult}.
 *
 * <p>Built-in commands:
 *
 * <ul>
 *   <li>{@code /help} - Shows available commands
 *   <li>{@code /status} - Shows current status
 * </ul>
 *
 * <p>New commands can be registered dynamically:
 *
 * <pre>
 * dispatcher.register("/custom", args -> CommandResult.success("Custom output"));
 * </pre>
 */
public final class CommandDispatcher {

  private static final String EMPTY_LINE = "";

  private final Map<String, Function<String, CommandResult>> handlers = new LinkedHashMap<>();

  private List<String> helpLines;

  /** Creates a CommandDispatcher with default commands registered. */
  public CommandDispatcher() {
    this.helpLines = defaultHelpLines();
    registerDefaults();
  }

  private void registerDefaults() {
    // /help command and "/" alias
    register("/help", args -> CommandResult.success(getHelpLines()));
    register("/", args -> CommandResult.success(getHelpLines()));
    // /status command
    register(
        "/status",
        args ->
            CommandResult.success(
                List.of(
                    msg("tui.command.status.header"),
                    EMPTY_LINE,
                    msg("tui.command.status.mode"),
                    msg("tui.command.status.state"),
                    msg("tui.command.status.ready"))));
  }

  /**
   * Registers a command handler.
   *
   * @param command the command name (must start with '/')
   * @param handler the handler function
   */
  public void register(final String command, final Function<String, CommandResult> handler) {
    final String normalizedCommand = normalizeCommandKey(command);
    if (handler == null) {
      throw new IllegalArgumentException(
          msg("tui.command.error.handler_required", normalizedCommand));
    }
    handlers.put(normalizedCommand, handler);
  }

  private static String normalizeCommandKey(final String command) {
    if (command == null) {
      throw new IllegalArgumentException(msg("tui.command.error.must_start_with_slash"));
    }
    final String normalized = command.trim();
    if (!normalized.startsWith("/")) {
      throw new IllegalArgumentException(msg("tui.command.error.must_start_with_slash"));
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  /**
   * Checks if the input is a slash command.
   *
   * @param input the user input
   * @return true if it starts with '/'
   */
  public boolean isCommand(final String input) {
    return input != null && input.trim().startsWith("/");
  }

  /**
   * Dispatches and executes a command.
   *
   * @param input the full command input (e.g., "/help" or "/status")
   * @return the command result
   */
  public CommandResult dispatch(final String input) {
    if (!isCommand(input)) {
      return CommandResult.error(msg("tui.command.not_a_command", input));
    }
    final String[] parts = input.trim().split("\\s+", 2);
    final String command = normalizeCommandKey(parts[0]);
    final String args = parts.length > 1 ? parts[1] : "";
    final Function<String, CommandResult> handler = handlers.get(command);
    if (handler == null) {
      // Codex-compatible UX: any slash-prefixed input falls back to command list.
      return CommandResult.success(getHelpLines());
    }
    try {
      final CommandResult result = handler.apply(args);
      if (result != null) {
        return result;
      }
    } catch (RuntimeException ignored) {
      // Keep slash-command failures inside the command surface instead of aborting the session.
    }
    return CommandResult.error(msg("tui.command.error.execution_failed", command));
  }

  /**
   * Returns the list of registered command names.
   *
   * @return list of command names
   */
  public List<String> getRegisteredCommands() {
    return List.copyOf(handlers.keySet());
  }

  /**
   * Replaces help lines shown by /help, "/" alias, and unknown slash command fallback.
   *
   * @param lines help lines to display
   */
  public void setHelpLines(final List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      this.helpLines = defaultHelpLines();
      return;
    }
    this.helpLines = List.copyOf(lines);
  }

  /**
   * Returns help lines for slash command palette rendering.
   *
   * @return immutable help lines
   */
  public List<String> getHelpLines() {
    return helpLines;
  }

  private static List<String> defaultHelpLines() {
    return List.of(
        EMPTY_LINE,
        msg("tut.help.help"),
        msg("tut.help.model"),
        msg("tut.help.status"),
        msg("tut.help.config"),
        msg("tut.help.config_get"),
        msg("tut.help.config_set"),
        msg("tut.help.config_search"),
        msg("tut.help.config_validate"),
        msg("tut.help.quit"));
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
