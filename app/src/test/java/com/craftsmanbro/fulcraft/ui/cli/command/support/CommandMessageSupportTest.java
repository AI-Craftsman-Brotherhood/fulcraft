package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class CommandMessageSupportTest {

  @Test
  void resolve_returnsProvidedBundle_whenNotNull() {
    ResourceBundle bundle = new TestBundle();
    ResourceBundle result = CommandMessageSupport.resolve(bundle);
    assertThat(result).isSameAs(bundle);
  }

  @Test
  void resolve_loadsDefaultBundle_whenNoBundleProvided() {
    ResourceBundle result = CommandMessageSupport.resolve(null);

    assertThat(result).isNotNull();
    assertThat(result.containsKey("report.title")).isTrue();
  }

  @Test
  void message_returnsKey_whenBundleIsNull() {
    String result = CommandMessageSupport.message(null, "some.key");
    assertThat(result).isEqualTo("some.key");
  }

  @Test
  void message_requiresNonNullKey() {
    assertThatThrownBy(() -> CommandMessageSupport.message(new TestBundle(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("key must not be null");
    assertThatThrownBy(() -> CommandMessageSupport.message(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("key must not be null");
  }

  @Test
  void message_returnsPatternAsIs_whenNoArgumentsProvided() {
    ResourceBundle bundle = new TestBundle();

    String result = CommandMessageSupport.message(bundle, "plain");

    assertThat(result).isEqualTo("Plain text");
  }

  @Test
  void message_returnsFormattedString_whenBundleHasKey() {
    ResourceBundle bundle = new TestBundle();
    String result = CommandMessageSupport.message(bundle, "hello", "World");
    assertThat(result).isEqualTo("Hello World");
  }

  @Test
  void message_returnsKey_whenBundleMissingKey() {
    ResourceBundle bundle = new TestBundle();
    String result = CommandMessageSupport.message(bundle, "missing.key");
    assertThat(result).isEqualTo("missing.key");
  }

  static class TestBundle extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {{"hello", "Hello {0}"}, {"plain", "Plain text"}};
    }
  }
}
