package com.craftsmanbro.fulcraft.infrastructure.version.contract;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.version.model.VersionInfo;
import java.nio.file.Path;

/** Contract for resolving version metadata used for cache key versioning and startup display. */
public interface VersionInfoResolverPort {

  /** Resolves version metadata from configuration content alone. */
  VersionInfo fromContent(String configContent);

  /** Resolves version metadata from configuration content and lockfile content. */
  VersionInfo fromContent(String configContent, String lockfileContent);

  /** Resolves version metadata from files in the target project. */
  VersionInfo fromProject(Path projectRoot, Path configPath, boolean includeLockfile);

  /** Resolves version metadata from a parsed config and project context. */
  VersionInfo fromConfig(Config config, Path projectRoot, boolean includeLockfile);

  /** Returns an empty version metadata value. */
  VersionInfo empty();

  /** Resolves the application version used for startup display. */
  String resolveApplicationVersion();
}
