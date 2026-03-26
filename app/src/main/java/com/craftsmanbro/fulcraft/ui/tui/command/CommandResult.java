package com.craftsmanbro.fulcraft.ui.tui.command;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.List;
import java.util.Objects;

/**
 * Result of executing a slash command.
 *
 * <p>Contains the output lines to display and metadata about the execution.
 */
public record CommandResult(boolean success, List<String> outputLines, String errorMessage) {

  /**
   * Creates a new CommandResult.
   *
   * @param success whether the command executed successfully
   * @param outputLines lines to display to the user (immutable copy made)
   * @param errorMessage error message if unsuccessful, null otherwise
   */
  public CommandResult {
    Objects.requireNonNull(outputLines, "outputLines must not be null");
    outputLines = List.copyOf(outputLines);
    if (success) {
      errorMessage = null;
    } else {
      errorMessage = normalizeErrorMessage(errorMessage);
    }
  }

  private static String normalizeErrorMessage(final String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return MessageSource.getMessage("tui.command_result.unknown_error");
    }
    return errorMessage;
  }

  /**
   * Creates a successful result with output lines.
   *
   * @param lines the output lines
   * @return a successful CommandResult
   */
  public static CommandResult success(final List<String> lines) {
    return new CommandResult(true, lines, null);
  }

  /**
   * Creates a successful result with a single line.
   *
   * @param line the output line
   * @return a successful CommandResult
   */
  public static CommandResult success(final String line) {
    return new CommandResult(true, List.of(line), null);
  }

  /**
   * Creates an error result.
   *
   * @param errorMessage the error message
   * @return an error CommandResult
   */
  public static CommandResult error(final String errorMessage) {
    return new CommandResult(false, List.of(), errorMessage);
  }
}
