package com.craftsmanbro.fulcraft.infrastructure.fs.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FsArtifactStoreTest {

  @TempDir Path tempDir;

  @Test
  void fromCreatesStoreWithRunRoot() {
    Path runRoot = tempDir.resolve("run-1");

    FsArtifactStore store = FsArtifactStore.from(runRoot);

    assertEquals(runRoot, store.runRoot());
  }

  @Test
  void constructorRejectsNullRunRoot() {
    NullPointerException ex =
        assertThrows(NullPointerException.class, () -> new FsArtifactStore(null));

    assertTrue(ex.getMessage().contains("runRoot"));
  }

  @Test
  void actionsRootCreatesDirectory() throws IOException {
    Path runRoot = tempDir.resolve("run-2");
    FsArtifactStore store = FsArtifactStore.from(runRoot);

    Path actionsRoot = store.actionsRoot();

    assertEquals(runRoot.resolve("actions"), actionsRoot);
    assertTrue(Files.isDirectory(actionsRoot));
  }

  @Test
  void actionsCreatesPluginDirectoryUnderActionsRoot() throws IOException {
    Path runRoot = tempDir.resolve("run-3");
    FsArtifactStore store = FsArtifactStore.from(runRoot);

    Path pluginDir = store.actions("junit");

    assertEquals(runRoot.resolve("actions").resolve("junit"), pluginDir);
    assertTrue(Files.isDirectory(pluginDir));
  }

  @Test
  void actionsRejectsNullAndBlankPluginId() {
    FsArtifactStore store = FsArtifactStore.from(tempDir.resolve("run-4"));

    assertThrows(NullPointerException.class, () -> store.actions(null));
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> store.actions("   "));
    assertTrue(ex.getMessage().contains("pluginId must not be blank"));
  }

  @Test
  void actionsRejectsPluginIdWithPathSeparators() {
    FsArtifactStore store = FsArtifactStore.from(tempDir.resolve("run-5"));

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> store.actions("../escape"));

    assertTrue(ex.getMessage().contains("pluginId must not contain path separators"));
  }

  @Test
  void actionsWithNodeRejectsNodeIdWithPathSeparators() {
    FsArtifactStore store = FsArtifactStore.from(tempDir.resolve("run-6"));

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> store.actions("junit", "../escape"));

    assertTrue(ex.getMessage().contains("nodeId must not contain path separators"));
  }
}
