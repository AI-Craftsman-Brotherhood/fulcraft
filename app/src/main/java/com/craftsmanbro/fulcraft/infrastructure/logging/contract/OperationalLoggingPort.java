package com.craftsmanbro.fulcraft.infrastructure.logging.contract;

import com.craftsmanbro.fulcraft.infrastructure.logging.model.LogContext;

/** Contract for application operational logging and mapped diagnostic context handling. */
public interface OperationalLoggingPort {

  void emitInfo(String message);

  void emitDebug(String message);

  void emitWarn(String message);

  void emitWarn(String message, Throwable throwable);

  void emitError(String message);

  void emitError(String message, Throwable throwable);

  /** Emits a plain standard-output line for CLI-style reporting. */
  void emitStdout(String message);

  /** Emits a plain standard-error line for CLI-style reporting. */
  void emitStderr(String message);

  /**
   * Applies mapped diagnostic context values for the current thread.
   *
   * @param context logging context values to apply
   */
  void applyContext(LogContext context);

  /**
   * Returns the mapped diagnostic context currently associated with the thread.
   *
   * @return current logging context snapshot
   */
  LogContext currentContext();
}
