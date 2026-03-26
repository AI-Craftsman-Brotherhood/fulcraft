package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmOpenQuestionBuilderTest {

  private final LlmOpenQuestionBuilder builder = new LlmOpenQuestionBuilder();

  @Test
  void buildInterfaceOpenQuestions_shouldIncludeOverviewAndDynamicCandidates() {
    Set<String> uncertainMethods = new LinkedHashSet<>();
    uncertainMethods.add("CustomerService#resolve(String)");
    uncertainMethods.add("OrderGateway#fetch()");

    List<String> questions = builder.buildInterfaceOpenQuestions(uncertainMethods, false);

    assertThat(questions)
        .startsWith(
            "Preconditions, side effects, and exception details are implementation-specific and cannot be finalized from interface analysis alone.")
        .anySatisfy(
            q ->
                assertThat(q)
                    .contains(
                        "Verify existence of dynamically resolved candidate method `CustomerService#resolve(String)`"))
        .anySatisfy(
            q ->
                assertThat(q)
                    .contains(
                        "Verify existence of dynamically resolved candidate method `OrderGateway#fetch()`"));
  }

  @Test
  void buildFallbackOpenQuestions_shouldReturnEmptyWhenMethodNamesMissing() {
    List<String> nullMethods =
        builder.buildFallbackOpenQuestions(
            null, Set.of("CustomerService#resolve(String)"), List.of("reason"), false);
    List<String> emptyMethods =
        builder.buildFallbackOpenQuestions(
            List.of(), Set.of("CustomerService#resolve(String)"), List.of("reason"), false);

    assertThat(nullMethods).isEmpty();
    assertThat(emptyMethods).isEmpty();
  }

  @Test
  void buildFallbackOpenQuestions_shouldExtractDeclaringTypeCheckFromValidationReasons() {
    Set<String> uncertainMethods = new LinkedHashSet<>();
    uncertainMethods.add("CustomerService#resolve(String)");
    uncertainMethods.add("");
    uncertainMethods.add(null);

    List<String> questions =
        builder.buildFallbackOpenQuestions(
            List.of("resolve"),
            uncertainMethods,
            List.of(
                "External method existence asserted: resolveCustomer",
                "外部メソッド存在を断定：resolveCustomer",
                "External method existence asserted: 1invalid",
                "External method existence asserted:",
                "unrelated"),
            false);

    assertThat(questions)
        .anySatisfy(
            q ->
                assertThat(q)
                    .contains(
                        "Verify existence of dynamically resolved candidate method `CustomerService#resolve(String)`"));
    assertThat(questions)
        .filteredOn(
            q -> q.contains("Confirm the declaring type of method `resolveCustomer` manually."))
        .hasSize(1);
    assertThat(questions).noneMatch(q -> q.contains("1invalid"));
  }

  @Test
  void buildFallbackOpenQuestions_shouldUseJapaneseMessagesWhenRequested() {
    List<String> questions =
        builder.buildFallbackOpenQuestions(
            List.of("resolve"), Set.of("注文Service#更新()"), List.of("外部メソッド存在を断定：findById"), true);

    assertThat(questions)
        .anySatisfy(q -> assertThat(q).contains("動的解決候補メソッド `注文Service#更新()` の実在確認が必要。"))
        .anySatisfy(q -> assertThat(q).contains("メソッド `findById` の定義元クラスを手動確認する必要がある。"));
  }
}
