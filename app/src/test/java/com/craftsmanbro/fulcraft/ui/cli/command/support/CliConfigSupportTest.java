package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.ConfigLoaderPort;
import com.craftsmanbro.fulcraft.config.ConfigOverride;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;

@Isolated
class CliConfigSupportTest {

  @Test
  void loadConfig_requiresNonNullConfigLoader() {
    assertThatThrownBy(() -> CliConfigSupport.loadConfig(null, Path.of("config.json"), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("configLoader must not be null");
  }

  @Test
  void loadConfig_delegatesWithProvidedOverrides() {
    ConfigLoaderPort loader = mock(ConfigLoaderPort.class);
    Path configPath = Path.of("config.json");
    ConfigOverride override = config -> {};
    List<ConfigOverride> overrides = List.of(override);
    Config expectedConfig = new Config();

    when(loader.load(eq(configPath), any(ConfigOverride[].class))).thenReturn(expectedConfig);

    Config result = CliConfigSupport.loadConfig(loader, configPath, overrides);

    assertThat(result).isSameAs(expectedConfig);
    ArgumentCaptor<ConfigOverride[]> captor = ArgumentCaptor.forClass(ConfigOverride[].class);
    verify(loader).load(eq(configPath), captor.capture());
    assertThat(captor.getValue()).containsExactly(override);
  }

  @Test
  void loadConfig_passesEmptyOverrideArray_whenOverrideListIsNull() {
    ConfigLoaderPort loader = mock(ConfigLoaderPort.class);
    Path configPath = Path.of("config.json");
    Config expectedConfig = new Config();
    when(loader.load(eq(configPath), any(ConfigOverride[].class))).thenReturn(expectedConfig);

    Config result = CliConfigSupport.loadConfig(loader, configPath, null);

    assertThat(result).isSameAs(expectedConfig);
    ArgumentCaptor<ConfigOverride[]> captor = ArgumentCaptor.forClass(ConfigOverride[].class);
    verify(loader).load(eq(configPath), captor.capture());
    assertThat(captor.getValue()).isEmpty();
  }

  @Test
  void loadConfig_allowsNullConfigPathForLoaderFallbackResolution() {
    ConfigLoaderPort loader = mock(ConfigLoaderPort.class);
    Config expectedConfig = new Config();
    when(loader.load(isNull(), any(ConfigOverride[].class))).thenReturn(expectedConfig);

    Config result = CliConfigSupport.loadConfig(loader, null, List.of());

    assertThat(result).isSameAs(expectedConfig);
    ArgumentCaptor<ConfigOverride[]> captor = ArgumentCaptor.forClass(ConfigOverride[].class);
    verify(loader).load(isNull(), captor.capture());
    assertThat(captor.getValue()).isEmpty();
  }
}
