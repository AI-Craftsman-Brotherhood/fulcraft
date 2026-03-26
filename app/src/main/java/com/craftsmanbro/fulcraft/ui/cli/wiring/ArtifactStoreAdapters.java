package com.craftsmanbro.fulcraft.ui.cli.wiring;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.FsArtifactStore;
import com.craftsmanbro.fulcraft.kernel.plugin.api.ArtifactStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Composition-root adapters between infrastructure filesystem stores and kernel plugin SPI. */
public final class ArtifactStoreAdapters {

  private ArtifactStoreAdapters() {}

  public static ArtifactStore fromRunRoot(final Path runRoot) {
    return fromFsStore(FsArtifactStore.from(runRoot));
  }

  public static ArtifactStore fromFsStore(final FsArtifactStore fsStore) {
    Objects.requireNonNull(fsStore, "fsStore");
    return new DelegatingArtifactStore(fsStore);
  }

  private record DelegatingArtifactStore(FsArtifactStore store) implements ArtifactStore {

    private DelegatingArtifactStore {
      Objects.requireNonNull(store, "store");
    }

    @Override
    public Path runRoot() {
      return store.runRoot();
    }

    @Override
    public Path actionsRoot() throws IOException {
      return store.actionsRoot();
    }

    @Override
    public Path actions(final String pluginId) throws IOException {
      return store.actions(pluginId);
    }
  }
}
