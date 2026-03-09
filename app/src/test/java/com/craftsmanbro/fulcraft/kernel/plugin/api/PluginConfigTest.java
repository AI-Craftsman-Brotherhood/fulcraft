package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PluginConfigTest {

  @Test
  void empty_returnsDefaultValues() {
    PluginConfig config = PluginConfig.empty();

    assertThat(config.getPluginId()).isEmpty();
    assertThat(config.getConfigPath()).isNull();
    assertThat(config.getSchemaPath()).isNull();
    assertThat(config.configExists()).isFalse();
    assertThat(config.getOptionalString("missing")).isEmpty();
  }

  @Test
  void constructor_throwsWhenConfigIsNull() {
    assertThatThrownBy(() -> new PluginConfig("demo", null, null, null, true))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "config"));
  }

  @Test
  void getters_returnProvidedValues() {
    Config delegate = Mockito.mock(Config.class);
    when(delegate.getPropertyNames()).thenReturn(List.of("foo", "bar"));
    when(delegate.getOptionalValue("foo", String.class)).thenReturn(Optional.of("value"));

    PluginConfig config =
        new PluginConfig(
            "demo",
            Path.of("plugins/demo/config.json"),
            Path.of("plugins/demo/schema.json"),
            delegate,
            true);

    assertThat(config.getPluginId()).contains("demo");
    assertThat(config.getConfigPath()).isEqualTo(Path.of("plugins/demo/config.json"));
    assertThat(config.getSchemaPath()).isEqualTo(Path.of("plugins/demo/schema.json"));
    assertThat(config.configExists()).isTrue();
    assertThat(config.getPropertyNames()).containsExactly("foo", "bar");
    assertThat(config.getOptionalValue("foo", String.class)).contains("value");
    assertThat(config.getOptionalString("foo")).contains("value");
    assertThat(config.getConfig()).isSameAs(delegate);
    verify(delegate).getPropertyNames();
    verify(delegate, times(2)).getOptionalValue("foo", String.class);
  }

  @Test
  void getOptionalValue_returnsEmptyWhenKeyIsNullOrBlank() {
    Config delegate = Mockito.mock(Config.class);
    PluginConfig config = new PluginConfig("demo", null, null, delegate, true);

    assertThat(config.getOptionalValue(null, String.class)).isEmpty();
    assertThat(config.getOptionalValue(" ", String.class)).isEmpty();

    verifyNoInteractions(delegate);
  }
}
