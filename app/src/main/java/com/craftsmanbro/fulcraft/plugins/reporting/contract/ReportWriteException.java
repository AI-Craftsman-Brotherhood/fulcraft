package com.craftsmanbro.fulcraft.plugins.reporting.contract;

/**
 * Exception thrown when report generation or writing fails.
 *
 * <p>This exception is used by {@link ReportWriterPort} implementations to indicate that an error
 * occurred during the REPORT phase of the pipeline. Common causes include:
 *
 * <ul>
 *   <li>I/O errors when writing report files
 *   <li>Invalid or incomplete report data
 *   <li>Output directory access issues
 *   <li>Template rendering failures (for HTML reports)
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * try {
 *   reportWriter.writeReport(data, config);
 * } catch (ReportWriteException e) {
 *   logger.error("Report generation failed: {}", e.getMessage());
 *   // Handle gracefully - reports are non-critical to test generation
 * }
 * }</pre>
 *
 * @see ReportWriterPort
 */
public class ReportWriteException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified detail message.
   *
   * @param message the detail message (saved for later retrieval by {@link #getMessage()})
   */
  public ReportWriteException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause (saved for later retrieval by {@link #getCause()})
   */
  public ReportWriteException(final String message, final Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with the specified cause.
   *
   * @param cause the cause (saved for later retrieval by {@link #getCause()})
   */
  public ReportWriteException(final Throwable cause) {
    super(cause);
  }
}
