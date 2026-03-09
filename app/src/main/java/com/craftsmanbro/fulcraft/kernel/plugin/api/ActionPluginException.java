package com.craftsmanbro.fulcraft.kernel.plugin.api;

/** Exception thrown when an action plugin execution fails. */
public class ActionPluginException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public ActionPluginException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public ActionPluginException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
