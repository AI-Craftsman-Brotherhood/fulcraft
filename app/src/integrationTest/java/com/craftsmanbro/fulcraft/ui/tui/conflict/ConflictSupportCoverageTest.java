package com.craftsmanbro.fulcraft.ui.tui.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConflictSupportCoverageTest {

  @TempDir Path tempDir;

  @Test
  @DisplayName("ConflictCandidate resolves names for explicit, blank, and root paths")
  void conflictCandidate_resolvesDisplayNameBranches() {
    Path existing = tempDir.resolve("ExistingTest.java");

    ConflictCandidate explicit =
        new ConflictCandidate(tempDir.resolve("TargetTest.java"), existing, "ExplicitName.java");
    assertEquals("ExplicitName.java", explicit.fileName());

    ConflictCandidate fallbackToExisting = new ConflictCandidate(Path.of(""), existing, "   ");
    assertEquals("ExistingTest.java", fallbackToExisting.fileName());

    Path rootPath = tempDir.getRoot();
    ConflictCandidate rootDisplay = new ConflictCandidate(rootPath, existing, "");
    assertEquals(rootPath.toString(), rootDisplay.fileName());

    ConflictCandidate fromTwoPaths = ConflictCandidate.of(tempDir.resolve("Sample.java"), existing);
    assertEquals("Sample.java", fromTwoPaths.fileName());

    Path singlePath = tempDir.resolve("Single.java");
    ConflictCandidate fromSinglePath = ConflictCandidate.of(singlePath);
    assertEquals(singlePath, fromSinglePath.targetPath());
    assertEquals(singlePath, fromSinglePath.existingPath());

    ConflictCandidate fromString = ConflictCandidate.of(singlePath.toString());
    assertEquals(singlePath, fromString.targetPath());
    assertEquals("Single.java", fromString.fileName());
  }

  @Test
  @DisplayName("ConflictCandidate enforces null guards")
  void conflictCandidate_nullGuards() {
    Path path = tempDir.resolve("Any.java");

    assertThrows(NullPointerException.class, () -> new ConflictCandidate(null, path, "name"));
    assertThrows(NullPointerException.class, () -> new ConflictCandidate(path, null, "name"));
    assertThrows(NullPointerException.class, () -> new ConflictCandidate(path, path, null));

    assertThrows(NullPointerException.class, () -> ConflictCandidate.of((Path) null, path));
    assertThrows(NullPointerException.class, () -> ConflictCandidate.of(path, null));
    assertThrows(NullPointerException.class, () -> ConflictCandidate.of((Path) null));
    assertThrows(NullPointerException.class, () -> ConflictCandidate.of((String) null));
  }

  @Test
  @DisplayName("ConflictDetector factories create default and dummy detectors")
  void conflictDetector_factoriesProvideExpectedImplementations() {
    ConflictDetector defaultDetector = ConflictDetector.createDefault(tempDir);
    assertInstanceOf(TaskPlanConflictDetector.class, defaultDetector);
    assertEquals(0, defaultDetector.getTotalFileCount());
    assertTrue(defaultDetector.detectConflicts().isEmpty());

    ConflictDetector dummyDetector = ConflictDetector.createDummy();
    assertInstanceOf(DummyConflictDetector.class, dummyDetector);

    List<ConflictCandidate> conflicts = dummyDetector.detectConflicts();
    assertEquals(3, conflicts.size());
    assertEquals(10, dummyDetector.getTotalFileCount());
    assertEquals("UserServiceTest.java", conflicts.get(0).fileName());
  }

  @Test
  @DisplayName("IssueHandlingOption resolves keys and labels")
  void issueHandlingOption_fromKeyNumberCoversBranches() {
    assertEquals(IssueHandlingOption.SAFE_FIX, IssueHandlingOption.fromKeyNumber(1));
    assertEquals(IssueHandlingOption.PROPOSE_ONLY, IssueHandlingOption.fromKeyNumber(2));
    assertEquals(IssueHandlingOption.SKIP, IssueHandlingOption.fromKeyNumber(3));
    assertNull(IssueHandlingOption.fromKeyNumber(0));
    assertNull(IssueHandlingOption.fromKeyNumber(99));

    for (IssueHandlingOption option : IssueHandlingOption.values()) {
      assertNotNull(option.getDisplayName());
      assertNotNull(option.getDescription());
      assertTrue(option.getMenuLabel().startsWith(option.getKeyNumber() + " - "));
    }
  }

  @Test
  @DisplayName("IssueCategory private validation rejects blank and null")
  void issueCategory_requireNonBlankValidationBranches() throws Exception {
    Method method =
        IssueCategory.class.getDeclaredMethod("requireNonBlank", String.class, String.class);
    method.setAccessible(true);

    assertEquals("ok", method.invoke(null, "ok", "label"));

    InvocationTargetException blankThrown =
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "  ", "label"));
    assertInstanceOf(IllegalArgumentException.class, blankThrown.getCause());

    InvocationTargetException nullThrown =
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, null, "label"));
    assertInstanceOf(NullPointerException.class, nullThrown.getCause());
  }

  @Test
  @DisplayName("ConflictPolicy exposes stable display names and descriptions")
  void conflictPolicy_gettersReturnMetadata() {
    assertEquals("Safe (new file)", ConflictPolicy.SAFE.getDisplayName());
    assertEquals("Skip", ConflictPolicy.SKIP.getDisplayName());
    assertEquals("Overwrite", ConflictPolicy.OVERWRITE.getDisplayName());

    for (ConflictPolicy policy : ConflictPolicy.values()) {
      assertNotNull(policy.getDescription());
      assertTrue(!policy.getDescription().isBlank());
    }
  }
}
