package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResolutionRuleIdTest {

  @Test
  void descriptions_areNotBlank() {
    for (ResolutionRuleId ruleId : ResolutionRuleId.values()) {
      assertThat(ruleId.getDescription()).isNotBlank();
    }
  }

  @Test
  void fromString_parsesCaseInsensitiveAndTrims() {
    assertThat(ResolutionRuleId.fromString("literal")).isEqualTo(ResolutionRuleId.LITERAL);
    assertThat(ResolutionRuleId.fromString("  STRING_FORMAT "))
        .isEqualTo(ResolutionRuleId.STRING_FORMAT);
  }

  @Test
  void fromString_returnsNullForBlankOrUnknown() {
    assertThat(ResolutionRuleId.fromString(" ")).isNull();
    assertThat(ResolutionRuleId.fromString("not_a_rule")).isNull();
    assertThat(ResolutionRuleId.fromString(null)).isNull();
  }
}
