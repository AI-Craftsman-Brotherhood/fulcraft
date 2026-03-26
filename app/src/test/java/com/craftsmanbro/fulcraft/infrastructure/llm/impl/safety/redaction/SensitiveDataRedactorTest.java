package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {

  @Test
  void redact_masksMultipleSensitiveTypes() {
    SensitiveDataRedactor redactor = new SensitiveDataRedactor();
    String text =
        "Email support@craftsmann-bro.com card 4242 4242 4242 4242 "
            + "Authorization: Bearer token123 jwt aaaaaaaaaa.bbbbbbbbbb.cccccccc "
            + "api_key=shhh";

    RedactionResult result = redactor.redact(text);

    assertThat(result.redactedText()).doesNotContain("support@craftsmann-bro.com");
    assertThat(result.redactedText()).doesNotContain("4242 4242 4242 4242");
    assertThat(result.redactedText()).doesNotContain("token123");
    assertThat(result.redactedText()).doesNotContain("shhh");
    assertThat(result.report().emailCount()).isEqualTo(1);
    assertThat(result.report().creditCardCount()).isEqualTo(1);
    assertThat(result.report().authTokenCount()).isEqualTo(2);
    assertThat(result.report().jwtCount()).isEqualTo(1);
  }

  @Test
  void redact_preservesQuotesForKeyValues() {
    SensitiveDataRedactor redactor = new SensitiveDataRedactor();
    String text = "password=\"topsecret\"";

    RedactionResult result = redactor.redact(text);

    assertThat(result.redactedText()).isEqualTo("password=\"[REDACTED]\"");
  }

  @Test
  void redact_doesNotMaskInvalidCreditCards() {
    SensitiveDataRedactor redactor = new SensitiveDataRedactor();
    String text = "card 1234 5678 9012 3456";

    RedactionResult result = redactor.redact(text);

    assertThat(result.redactedText()).contains("1234 5678 9012 3456");
    assertThat(result.report().creditCardCount()).isEqualTo(0);
  }

  @Test
  void redact_withCustomMask_appliesGivenMaskLiteral() {
    SensitiveDataRedactor redactor = new SensitiveDataRedactor("***");

    RedactionResult result = redactor.redact("token=secret");

    assertThat(result.redactedText()).isEqualTo("token=***");
  }

  @Test
  void redact_masksSingleQuotedKeyValue() {
    SensitiveDataRedactor redactor = new SensitiveDataRedactor();

    RedactionResult result = redactor.redact("password='topsecret'");

    assertThat(result.redactedText()).isEqualTo("password='[REDACTED]'");
  }

  @Test
  void constructorAndRedact_rejectNullInputs() {
    assertThatThrownBy(() -> new SensitiveDataRedactor(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SensitiveDataRedactor().redact(null))
        .isInstanceOf(NullPointerException.class);
  }
}
