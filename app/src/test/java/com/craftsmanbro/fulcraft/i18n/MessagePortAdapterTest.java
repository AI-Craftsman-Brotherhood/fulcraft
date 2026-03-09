package com.craftsmanbro.fulcraft.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class MessagePortAdapterTest {

  private MessagePortAdapter adapter;
  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    adapter = new MessagePortAdapter();
    originalLocale = MessageSource.getLocale();
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(originalLocale);
  }

  @Test
  void getMessage_delegatesToMessageSource() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(adapter.getMessage("cli.start")).isEqualTo("Starting FUL...");
  }

  @Test
  void getMessage_withArgs_delegatesToMessageSource() {
    MessageSource.setLocale(Locale.ENGLISH);

    assertThat(adapter.getMessage("cli.version", "2.0")).isEqualTo("FUL version 2.0");
  }

  @Test
  void getLocale_delegatesToMessageSource() {
    MessageSource.setLocale(Locale.FRENCH);

    assertThat(adapter.getLocale()).isEqualTo(Locale.FRENCH);
  }
}
