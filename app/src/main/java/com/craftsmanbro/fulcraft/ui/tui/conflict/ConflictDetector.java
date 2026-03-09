package com.craftsmanbro.fulcraft.ui.tui.conflict;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for detecting file conflicts before test generation.
 *
 * <p>Implementations of this interface scan the target output directory and the planned generation
 * targets to identify files that would conflict.
 *
 * <p>Currently, a dummy implementation is provided for TUI development. The actual implementation
 * will be connected to the Plan/Execution phase.
 */
public interface ConflictDetector {

  /**
   * Creates a default ConflictDetector that loads planned tasks from the project.
   *
   * <p>This detector looks for a tasks file in the project root or the latest run plan directory.
   *
   * @param projectRoot the project root directory
   * @return a default ConflictDetector
   */
  static ConflictDetector createDefault(final Path projectRoot) {
    return new TaskPlanConflictDetector(projectRoot);
  }

  /**
   * Detects files that would conflict with existing files.
   *
   * @return a list of conflict candidates, empty if no conflicts
   */
  List<ConflictCandidate> detectConflicts();

  /**
   * Returns the total number of files to be generated.
   *
   * @return total file count
   */
  int getTotalFileCount();

  /**
   * Creates a dummy ConflictDetector for testing/development purposes.
   *
   * <p>This implementation returns a fixed set of mock conflict candidates to support TUI
   * development before the actual conflict detection is implemented.
   *
   * @return a dummy ConflictDetector
   */
  static ConflictDetector createDummy() {
    return new DummyConflictDetector();
  }
}

/** Dummy implementation of ConflictDetector for development. */
class DummyConflictDetector implements ConflictDetector {

  private static final List<ConflictCandidate> DUMMY_CONFLICT_CANDIDATES =
      List.of(
          ConflictCandidate.of("src/test/java/com/example/UserServiceTest.java"),
          ConflictCandidate.of("src/test/java/com/example/OrderServiceTest.java"),
          ConflictCandidate.of("src/test/java/com/example/PaymentServiceTest.java"));

  private static final int DUMMY_TOTAL_FILE_COUNT = 10;

  @Override
  public List<ConflictCandidate> detectConflicts() {
    return DUMMY_CONFLICT_CANDIDATES;
  }

  @Override
  public int getTotalFileCount() {
    return DUMMY_TOTAL_FILE_COUNT;
  }
}
