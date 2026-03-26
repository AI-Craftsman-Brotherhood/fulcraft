package com.craftsmanbro.fulcraft.infrastructure.fs.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TestFilePlanTest {

  @Test
  void createsPlanWithGivenPathAndClassName() {
    Path testFile = Path.of("src/test/java/com/example/FooTest.java");

    TestFilePlan plan = new TestFilePlan(testFile, "FooTest");

    assertEquals(testFile, plan.testFile());
    assertEquals("FooTest", plan.testClassName());
  }

  @Test
  void rejectsNullTestFile() {
    NullPointerException ex =
        assertThrows(NullPointerException.class, () -> new TestFilePlan(null, "FooTest"));

    assertTrue(ex.getMessage().endsWith("testFile must not be null"), ex.getMessage());
  }

  @Test
  void rejectsNullTestClassName() {
    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> new TestFilePlan(Path.of("src/test/java/com/example/FooTest.java"), null));

    assertTrue(ex.getMessage().endsWith("testClassName must not be null"), ex.getMessage());
  }
}
