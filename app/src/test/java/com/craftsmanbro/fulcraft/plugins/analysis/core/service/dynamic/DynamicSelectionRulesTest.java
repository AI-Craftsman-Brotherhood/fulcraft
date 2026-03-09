package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.craftsmanbro.fulcraft.config.Config;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicSelectionRulesTest {

  @Test
  void evaluate_appliesPenaltiesAndSkip() {
    Config.SelectionRules config = new Config.SelectionRules();
    DynamicSelectionRules rules = new DynamicSelectionRules(config);

    DynamicSelectionFeatures features =
        new DynamicSelectionFeatures(0.4, 2, 1, true, 0.7, 1, 0, 0, 0);

    DynamicSelectionRules.RuleDecision decision = rules.evaluate(features);

    assertThat(decision.scorePenalty()).isCloseTo(1.0, within(0.0001));
    assertThat(decision.shouldSkip()).isTrue();
    assertThat(decision.reasons())
        .contains(
            "low_confidence(0.40)<0.80",
            "unresolved(2)*0.10",
            "external(1)*0.20",
            "spi_low_conf",
            "SKIP:min_conf<0.50");
  }

  @Test
  void evaluate_noPenaltyWhenHealthy() {
    Config.SelectionRules config = new Config.SelectionRules();
    DynamicSelectionRules rules = new DynamicSelectionRules(config);

    DynamicSelectionFeatures features =
        new DynamicSelectionFeatures(0.85, 0, 0, true, 0.9, 0, 0, 0, 0);

    DynamicSelectionRules.RuleDecision decision = rules.evaluate(features);

    assertThat(decision.scorePenalty()).isCloseTo(0.0, within(0.0001));
    assertThat(decision.shouldSkip()).isFalse();
    assertThat(decision.reasons()).isEqualTo(List.of());
  }

  @Test
  void ruleDecision_normalizesReasonsToImmutableList() {
    DynamicSelectionRules.RuleDecision nullReasons =
        new DynamicSelectionRules.RuleDecision(0.0, false, null);
    assertThat(nullReasons.reasons()).isEmpty();

    ArrayList<String> mutableReasons = new ArrayList<>();
    mutableReasons.add("first");

    DynamicSelectionRules.RuleDecision copiedReasons =
        new DynamicSelectionRules.RuleDecision(0.3, true, mutableReasons);
    mutableReasons.add("second");

    assertThat(copiedReasons.reasons()).containsExactly("first");
    assertThatThrownBy(() -> copiedReasons.reasons().add("third"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
