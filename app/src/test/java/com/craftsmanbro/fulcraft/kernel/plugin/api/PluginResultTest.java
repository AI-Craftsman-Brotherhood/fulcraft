package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PluginResultTest {

  @Nested
  class SuccessFactory {

    @Test
    void shouldCreateSuccessResult() {
      PluginResult result = PluginResult.success("plugin-1", "done");

      assertThat(result.getPluginId()).isEqualTo("plugin-1");
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getMessage()).isEqualTo("done");
      assertThat(result.getError()).isNull();
    }

    @Test
    void shouldAllowNullMessage() {
      PluginResult result = PluginResult.success("plugin-2", null);

      assertThat(result.getMessage()).isNull();
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldThrowOnNullPluginId() {
      assertThatThrownBy(() -> PluginResult.success(null, "msg"))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    }
  }

  @Nested
  class FailureFactory {

    @Test
    void shouldCreateFailureResult() {
      Exception error = new RuntimeException("boom");

      PluginResult result = PluginResult.failure("plugin-4", "failed", error);

      assertThat(result.getPluginId()).isEqualTo("plugin-4");
      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getMessage()).isEqualTo("failed");
      assertThat(result.getError()).isSameAs(error);
    }

    @Test
    void shouldAllowNullMessageAndError() {
      PluginResult result = PluginResult.failure("plugin-5", null, null);

      assertThat(result.getMessage()).isNull();
      assertThat(result.getError()).isNull();
      assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void shouldThrowOnNullPluginId() {
      assertThatThrownBy(() -> PluginResult.failure(null, "msg", new Exception()))
          .isInstanceOf(NullPointerException.class)
          .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "pluginId"));
    }
  }
}
