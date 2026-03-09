package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PathUtilsTest {

  @Test
  void getFileNameReturnsUnknownForNullAndTrailingSeparator() {
    assertEquals("unknown", PathUtils.getFileName(null));
    assertEquals("unknown", PathUtils.getFileName("src/main/java/"));
    assertEquals("unknown", PathUtils.getFileName("C:\\work\\src\\"));
  }

  @Test
  void getFileNameExtractsFromUnixAndWindowsPaths() {
    assertEquals("Foo.java", PathUtils.getFileName("/tmp/project/Foo.java"));
    assertEquals("Bar.java", PathUtils.getFileName("C:\\tmp\\project\\Bar.java"));
    assertEquals("Baz.java", PathUtils.getFileName("Baz.java"));
  }

  @Test
  void classNameToPathReturnsNullForNullOrEmptyInput() {
    assertNull(PathUtils.classNameToPath(null, PathUtils.JAVA_EXTENSION));
    assertNull(PathUtils.classNameToPath("", PathUtils.JAVA_EXTENSION));
  }

  @Test
  void classNameToPathConvertsDotsAndAppendsExtension() {
    assertEquals(
        "com/example/Foo.java",
        PathUtils.classNameToPath("com.example.Foo", PathUtils.JAVA_EXTENSION));
    assertEquals("com/example/Foo", PathUtils.classNameToPath("com.example.Foo", null));
  }

  @Test
  void getDirectoryExtractsDirectoryPortion() {
    assertEquals("", PathUtils.getDirectory(null));
    assertEquals("src/main/java", PathUtils.getDirectory("src/main/java/Foo.java"));
    assertEquals("C:\\work\\src", PathUtils.getDirectory("C:\\work\\src\\Foo.java"));
    assertEquals("", PathUtils.getDirectory("Foo.java"));
  }
}
