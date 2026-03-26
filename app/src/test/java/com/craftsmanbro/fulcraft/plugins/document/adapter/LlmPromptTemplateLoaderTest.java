package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmPromptTemplateLoader;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmPromptTemplateLoaderTest {

  private Locale previousLocale;

  @BeforeEach
  void captureLocale() {
    previousLocale = MessageSource.getLocale();
  }

  @AfterEach
  void restoreLocale() {
    if (previousLocale != null) {
      MessageSource.setLocale(previousLocale);
      return;
    }
    MessageSource.initialize();
  }

  @Test
  void loadPromptTemplate_usesJapaneseResourceForJapaneseLocale() {
    MessageSource.setLocale(Locale.JAPANESE);

    String template = new LlmPromptTemplateLoader().loadPromptTemplate();

    assertThat(template).contains("## 解析情報");
    assertThat(template).contains("## 出力フォーマット（厳守）");
  }

  @Test
  void loadPromptTemplate_usesEnglishResourceForEnglishLocale() {
    MessageSource.setLocale(Locale.ENGLISH);

    String template = new LlmPromptTemplateLoader().loadPromptTemplate();

    assertThat(template).contains("## Analysis Information");
    assertThat(template).contains("## Output Format (Strict)");
  }
}
