package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import org.junit.jupiter.api.Test;

class PluginKindTest {

  @Test
  void normalizeRequiredShouldLowercaseAndTrim() {
    assertThat(PluginKind.normalizeRequired("  WORKFLOW  ", "kind")).isEqualTo("workflow");
  }

  @Test
  void normalizeRequiredShouldRejectBlank() {
    assertThatThrownBy(() -> PluginKind.normalizeRequired(" ", "kind"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(MessageSource.getMessage("kernel.plugin.kind.error.blank", "kind"));
  }

  @Test
  void normalizeNullableShouldReturnNullForBlank() {
    assertThat(PluginKind.normalizeNullable(" ")).isNull();
  }

  @Test
  void normalizeNullableShouldLowercaseAndTrim() {
    assertThat(PluginKind.normalizeNullable("  REPORT  ")).isEqualTo("report");
  }
}
