package com.craftsmanbro.fulcraft.plugins.analysis.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UnresolvedReasonTest {

  private Locale previousLocale;

  @BeforeEach
  void setUp() {
    previousLocale = MessageSource.getLocale();
    MessageSource.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() {
    MessageSource.setLocale(previousLocale);
  }

  @Test
  void getMessageKey_returnsExpectedKeys() {
    assertThat(UnresolvedReason.MISSING_CLASSPATH.getMessageKey())
        .isEqualTo("analysis.unresolved.missing_classpath");
    assertThat(UnresolvedReason.GENERICS_ERASED.getMessageKey())
        .isEqualTo("analysis.unresolved.generics_erased");
    assertThat(UnresolvedReason.REFLECTION_CALL.getMessageKey())
        .isEqualTo("analysis.unresolved.reflection_call");
    assertThat(UnresolvedReason.NO_CLASSPATH_MODE.getMessageKey())
        .isEqualTo("analysis.unresolved.no_classpath_mode");
    assertThat(UnresolvedReason.PARSE_ERROR.getMessageKey())
        .isEqualTo("analysis.unresolved.parse_error");
    assertThat(UnresolvedReason.UNKNOWN.getMessageKey()).isEqualTo("analysis.unresolved.unknown");
  }

  @Test
  void getDescription_returnsLocalizedMessage() {
    UnresolvedReason reason = UnresolvedReason.MISSING_CLASSPATH;

    String description = reason.getDescription();

    assertThat(description).isEqualTo("Required dependency not in classpath");
  }

  @ParameterizedTest
  @CsvSource({
    "missing_classpath, MISSING_CLASSPATH",
    "GENERICS_ERASED, GENERICS_ERASED",
    " reflection_call , REFLECTION_CALL",
    "No_ClassPath_Mode, NO_CLASSPATH_MODE",
    "parse_error, PARSE_ERROR",
    "unknown, UNKNOWN"
  })
  void fromString_parsesCaseInsensitiveValues(String input, UnresolvedReason expected) {
    assertThat(UnresolvedReason.fromString(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "not_a_reason"})
  void fromString_returnsUnknownForBlankOrInvalid(String input) {
    assertThat(UnresolvedReason.fromString(input)).isEqualTo(UnresolvedReason.UNKNOWN);
  }
}
