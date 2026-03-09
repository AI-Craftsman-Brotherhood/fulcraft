package com.craftsmanbro.fulcraft.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class MessageSourceTest {

  private Locale originalLocale;
  private String originalFulLangProp;

  @BeforeEach
  void setUp() {
    originalLocale = MessageSource.getLocale();
    originalFulLangProp = System.getProperty("ful.lang");
  }

  @AfterEach
  void tearDown() {
    if (originalFulLangProp != null) {
      System.setProperty("ful.lang", originalFulLangProp);
    } else {
      System.clearProperty("ful.lang");
    }
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void getMessage_returnsLocalizedMessage_whenLocaleIsEnglish() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(MessageSource.getMessage("cli.start")).isEqualTo("Starting FUL...");
  }

  @Test
  void getMessage_returnsLocalizedMessage_whenLocaleIsJapanese() {
    MessageSource.setLocale(Locale.JAPANESE);

    assertThat(MessageSource.getMessage("cli.start")).isEqualTo("FULを開始します...");
  }

  @Test
  void getMessage_returnsKey_whenKeyIsMissing() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(MessageSource.getMessage("non.existent.key")).isEqualTo("non.existent.key");
  }

  @Test
  void getMessage_returnsEmptyString_whenKeyIsNull() {
    assertThat(MessageSource.getMessage(null)).isEmpty();
  }

  @Test
  void getMessage_withArgs_formatsMessageCorrectly() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(MessageSource.getMessage("cli.version", "1.0.0")).isEqualTo("FUL version 1.0.0");
  }

  @Test
  void getMessage_withArgs_formatsMessageCorrectlyInJapanese() {
    MessageSource.setLocale(Locale.JAPANESE);

    assertThat(MessageSource.getMessage("cli.version", "1.0.0")).isEqualTo("FUL バージョン 1.0.0");
  }

  @Test
  void getMessage_withArgs_returnsPattern_whenArgsAreNullOrEmpty() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(MessageSource.getMessage("cli.version", (Object[]) null))
        .isEqualTo("FUL version {0}");
    assertThat(MessageSource.getMessage("cli.version")).isEqualTo("FUL version {0}");
  }

  @Test
  void getLocale_returnsCurrentLocale() {
    MessageSource.setLocale(Locale.CANADA);
    assertThat(MessageSource.getLocale()).isEqualTo(Locale.CANADA);
  }

  @Test
  void setLocale_defaultsToJapanese_whenLocaleIsNull() {
    MessageSource.setLocale(null);
    assertThat(MessageSource.getLocale()).isEqualTo(Locale.JAPANESE);
  }

  @Test
  void initialize_detectsLocaleFromSystemProperty() {
    System.setProperty("ful.lang", "ja");
    MessageSource.initialize();

    assertThat(MessageSource.getLocale()).isEqualTo(Locale.JAPANESE);
    assertThat(MessageSource.getMessage("cli.start")).isEqualTo("FULを開始します...");
  }

  @Test
  void initialize_defaultsToSystemDefault_whenPropertyMissing() {
    System.clearProperty("ful.lang");
    Locale systemDefault = Locale.getDefault();

    MessageSource.initialize();

    assertThat(MessageSource.getLocale()).isEqualTo(systemDefault);
  }
}
