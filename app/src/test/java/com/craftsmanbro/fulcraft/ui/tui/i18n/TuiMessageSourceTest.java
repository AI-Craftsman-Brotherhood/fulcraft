package com.craftsmanbro.fulcraft.ui.tui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;

class TuiMessageSourceTest {

  @Test
  void usesSpecifiedLocaleWhenConstructedWithLocale() {
    TuiMessageSource source = new TuiMessageSource(Locale.JAPANESE);

    assertThat(source.getLocale()).isEqualTo(Locale.JAPANESE);
  }

  @Test
  void usesDefaultLocaleWhenConstructedWithNullLocale() {
    TuiMessageSource source = new TuiMessageSource(null);

    assertThat(source.getLocale()).isEqualTo(Locale.getDefault());
  }

  @Test
  void returnsFreshInstanceWhenUsingGetDefault() {
    TuiMessageSource first = TuiMessageSource.getDefault();
    TuiMessageSource second = TuiMessageSource.getDefault();

    assertThat(first).isNotSameAs(second);
    assertThat(first.getLocale()).isEqualTo(Locale.getDefault());
    assertThat(second.getLocale()).isEqualTo(Locale.getDefault());
  }

  @Test
  void returnsEmptyStringWhenKeyIsNull() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);

    assertThat(source.getMessage(null)).isEmpty();
  }

  @Test
  void returnsKeyWhenBundleDoesNotContainKey() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);

    assertThat(source.getMessage("unknown.message.key")).isEqualTo("unknown.message.key");
  }

  @Test
  void returnsKeyWhenBundleIsNull() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, null);

    assertThat(source.getMessage("cli.start")).isEqualTo("cli.start");
  }

  @Test
  void returnsPatternWhenArgsIsNull() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("sample.key", "message"));

    assertThat(source.getMessage("sample.key", (Object[]) null)).isEqualTo("message");
  }

  @Test
  void returnsPatternWhenArgsIsEmpty() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("sample.key", "message"));

    assertThat(source.getMessage("sample.key", new Object[0])).isEqualTo("message");
  }

  @Test
  void formatsMessageWhenArgsProvided() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("sample.key", "hello {0}"));

    assertThat(source.getMessage("sample.key", "world")).isEqualTo("hello world");
  }

  @Test
  void returnsRawPatternWhenFormattingFails() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("sample.key", "bad pattern {"));

    assertThat(source.getMessage("sample.key", "world")).isEqualTo("bad pattern {");
  }

  @Test
  void updatesLocaleAndReloadsBundleWhenSetLocaleIsCalled() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("cli.start", "fake-message"));

    source.setLocale(Locale.JAPANESE);

    assertThat(source.getLocale()).isEqualTo(Locale.JAPANESE);
    assertThat(source.getMessage("cli.start"))
        .isNotEqualTo("fake-message")
        .isNotEqualTo("cli.start");
  }

  @Test
  void usesDefaultLocaleWhenSetLocaleReceivesNull() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);

    source.setLocale(null);

    assertThat(source.getLocale()).isEqualTo(Locale.getDefault());
  }

  @Test
  void reloadRestoresResourceBundleFromCurrentLocale() {
    TuiMessageSource source = new TuiMessageSource(Locale.ENGLISH);
    setBundle(source, bundleWith("cli.start", "fake-message"));

    source.reload();

    assertThat(source.getMessage("cli.start"))
        .isNotEqualTo("fake-message")
        .isNotEqualTo("cli.start");
  }

  @Test
  void hasExpectedMessageKeyConstant() {
    assertThat(TuiMessageSource.HOME_TITLE).isEqualTo("tui.home.title");
  }

  private static ResourceBundle bundleWith(String key, String value) {
    return new ListResourceBundle() {
      @Override
      protected Object[][] getContents() {
        return new Object[][] {{key, value}};
      }
    };
  }

  private static void setBundle(TuiMessageSource source, ResourceBundle bundle) {
    try {
      Field bundleField = TuiMessageSource.class.getDeclaredField("bundle");
      bundleField.setAccessible(true);
      bundleField.set(source, bundle);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to update message bundle for test setup", e);
    }
  }
}
