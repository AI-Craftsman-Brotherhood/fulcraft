package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import org.junit.jupiter.api.Test;

/** Unit tests for CLI cache configuration overrides. */
class CacheConfigCliTest {

  @Test
  void testApplyCliOverrides_AllFields() {
    Config config = Config.createDefault();
    CommonOverrides overrides =
        new CommonOverrides()
            .withCacheTtl(30)
            .withCacheRevalidate(true)
            .withCacheEncrypt(true)
            .withCacheKeyEnv("MY_KEY")
            .withCacheMaxSizeMb(100)
            .withCacheVersionCheck(true);

    overrides.apply(config);

    assertThat(config.getCache()).isNotNull();
    assertThat(config.getCache().getTtlDays()).isEqualTo(30);
    assertThat(config.getCache().isRevalidate()).isTrue();
    assertThat(config.getCache().isEncrypt()).isTrue();
    assertThat(config.getCache().getEncryptionKeyEnv()).isEqualTo("MY_KEY");
    assertThat(config.getCache().getMaxSizeMb()).isEqualTo(100);
    assertThat(config.getCache().isVersionCheck()).isTrue();
  }

  @Test
  void testApplyCliOverrides_Partial() {
    Config config = Config.createDefault();
    config.getCache().setTtlDays(7); // Default in config
    config.getCache().setEncrypt(false);
    CommonOverrides overrides = new CommonOverrides().withCacheEncrypt(true);

    overrides.apply(config);

    // Ttl should remain 7, encrypt should be true
    assertThat(config.getCache().getTtlDays()).isEqualTo(7);
    assertThat(config.getCache().isEncrypt()).isTrue();
  }
}
