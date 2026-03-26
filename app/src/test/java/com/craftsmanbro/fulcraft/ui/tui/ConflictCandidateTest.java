package com.craftsmanbro.fulcraft.ui.tui;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.ui.tui.conflict.ConflictCandidate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link ConflictCandidate}. */
class ConflictCandidateTest {

  @Nested
  @DisplayName("Construction")
  class ConstructionTests {

    @Test
    @DisplayName("Should create with valid arguments")
    void shouldCreateWithValidArguments() {
      Path target = Path.of("src/test/UserServiceTest.java");
      Path existing = Path.of("src/test/UserServiceTest.java");

      ConflictCandidate candidate = new ConflictCandidate(target, existing, "UserServiceTest.java");

      assertEquals(target, candidate.targetPath());
      assertEquals(existing, candidate.existingPath());
      assertEquals("UserServiceTest.java", candidate.fileName());
    }

    @Test
    @DisplayName("Should throw on null targetPath")
    void shouldThrowOnNullTargetPath() {
      assertThrows(
          NullPointerException.class, () -> new ConflictCandidate(null, Path.of("test"), "test"));
    }

    @Test
    @DisplayName("Should throw on null existingPath")
    void shouldThrowOnNullExistingPath() {
      assertThrows(
          NullPointerException.class, () -> new ConflictCandidate(Path.of("test"), null, "test"));
    }

    @Test
    @DisplayName("Should throw on null fileName")
    void shouldThrowOnNullFileName() {
      assertThrows(
          NullPointerException.class,
          () -> new ConflictCandidate(Path.of("test"), Path.of("test"), null));
    }
  }

  @Nested
  @DisplayName("Factory methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("of(Path) should create candidate with same target and existing path")
    void ofPathShouldCreateCorrectly() {
      Path path = Path.of("src/test/FooTest.java");

      ConflictCandidate candidate = ConflictCandidate.of(path);

      assertEquals(path, candidate.targetPath());
      assertEquals(path, candidate.existingPath());
      assertEquals("FooTest.java", candidate.fileName());
    }

    @Test
    @DisplayName("of(String) should create candidate from path string")
    void ofStringShouldCreateCorrectly() {
      ConflictCandidate candidate = ConflictCandidate.of("src/test/BarTest.java");

      assertEquals(Path.of("src/test/BarTest.java"), candidate.targetPath());
      assertEquals("BarTest.java", candidate.fileName());
    }

    @Test
    @DisplayName("of(Path) should throw on null")
    void ofPathShouldThrowOnNull() {
      assertThrows(NullPointerException.class, () -> ConflictCandidate.of((Path) null));
    }

    @Test
    @DisplayName("of(String) should throw on null")
    void ofStringShouldThrowOnNull() {
      assertThrows(NullPointerException.class, () -> ConflictCandidate.of((String) null));
    }
  }
}
