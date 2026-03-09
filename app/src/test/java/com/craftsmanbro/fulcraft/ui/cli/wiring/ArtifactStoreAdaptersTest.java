package com.craftsmanbro.fulcraft.ui.cli.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.FsArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactStoreAdaptersTest {

  @TempDir Path tempDir;

  @Test
  void fromRunRoot_adaptsFsStoreToKernelArtifactStore() throws IOException {
    Path runRoot = tempDir.resolve("run-1");

    ArtifactStore artifactStore = ArtifactStoreAdapters.fromRunRoot(runRoot);

    assertThat(artifactStore.runRoot()).isEqualTo(runRoot);
    Path actionsRoot = artifactStore.actionsRoot();
    Path pluginDir = artifactStore.actions("junit");
    assertThat(actionsRoot).isEqualTo(runRoot.resolve("actions"));
    assertThat(pluginDir).isEqualTo(runRoot.resolve("actions").resolve("junit"));
    assertThat(Files.isDirectory(actionsRoot)).isTrue();
    assertThat(Files.isDirectory(pluginDir)).isTrue();
  }

  @Test
  void fromFsStore_rejectsNullStore() {
    assertThatThrownBy(() -> ArtifactStoreAdapters.fromFsStore(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("fsStore");
  }

  @Test
  void fromRunRoot_rejectsNullRunRoot() {
    assertThatThrownBy(() -> ArtifactStoreAdapters.fromRunRoot(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("runRoot");
  }

  @Test
  void adaptedStore_preservesPluginIdValidation() {
    ArtifactStore artifactStore = ArtifactStoreAdapters.fromFsStore(FsArtifactStore.from(tempDir));

    assertThatThrownBy(() -> artifactStore.actions("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith("pluginId must not be blank");
  }
}
