package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictDetector;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConflictDetector}. */
class ConflictDetectorTest {

  @Nested
  @DisplayName("Dummy implementation")
  class DummyImplementationTests {

    @Test
    @DisplayName("createDummy should return non-null detector")
    void createDummyShouldReturnNonNull() {
      ConflictDetector detector = ConflictDetector.createDummy();

      assertNotNull(detector);
    }

    @Test
    @DisplayName("Dummy detector should return conflicts")
    void dummyDetectorShouldReturnConflicts() {
      ConflictDetector detector = ConflictDetector.createDummy();

      List<ConflictCandidate> conflicts = detector.detectConflicts();

      assertNotNull(conflicts);
      assertFalse(conflicts.isEmpty());
    }

    @Test
    @DisplayName("Dummy detector should return positive total file count")
    void dummyDetectorShouldReturnPositiveTotalCount() {
      ConflictDetector detector = ConflictDetector.createDummy();

      int totalCount = detector.getTotalFileCount();

      assertTrue(totalCount > 0);
    }

    @Test
    @DisplayName("Dummy conflict candidates should have valid file names")
    void dummyConflictsShouldHaveValidFileNames() {
      ConflictDetector detector = ConflictDetector.createDummy();

      List<ConflictCandidate> conflicts = detector.detectConflicts();

      for (ConflictCandidate candidate : conflicts) {
        assertNotNull(candidate.fileName());
        assertTrue(candidate.fileName().endsWith(".java"));
      }
    }
  }
}
