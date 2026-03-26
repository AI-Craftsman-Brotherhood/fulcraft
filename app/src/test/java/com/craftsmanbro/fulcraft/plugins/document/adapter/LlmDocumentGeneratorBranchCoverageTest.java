package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.PromptRedactionService;
import com.craftsmanbro.fulcraft.infrastructure.llm.model.ProviderProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LlmDocumentGeneratorBranchCoverageTest {

  private final LlmDocumentGenerator generator =
      new LlmDocumentGenerator(new NoopLlmClient(), new PromptRedactionService());

  @Test
  void containsFailureLikeFlow_shouldDetectKnownFailureTokens() {
    assertThat(invokeContainsFailureLikeFlow(null)).isFalse();
    assertThat(invokeContainsFailureLikeFlow("  ")).isFalse();
    assertThat(invokeContainsFailureLikeFlow("normal flow only")).isFalse();
    assertThat(invokeContainsFailureLikeFlow("early-return due to precondition")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("early return path")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("failure path")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("error path")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("boundary case")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("失敗ケース")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("異常系")).isTrue();
    assertThat(invokeContainsFailureLikeFlow("境界値")).isTrue();
  }

  @Test
  void normalizeMatchToken_shouldStripQuotesPunctuationAndWhitespace() {
    assertThat(invokeNormalizeMatchToken(null)).isEmpty();
    assertThat(invokeNormalizeMatchToken("   ")).isEmpty();
    assertThat(invokeNormalizeMatchToken(" `User` == \"A\" 'X' 。 、 ")).isEqualTo("user==ax");
  }

  @Test
  void resolveInlineNoneValue_shouldHandleLanguageAndTokenVariants() {
    assertThat(invokeResolveInlineNoneValue(null, true)).isEqualTo("なし");
    assertThat(invokeResolveInlineNoneValue(null, false)).isEqualTo("None");
    assertThat(invokeResolveInlineNoneValue("none", true)).isEqualTo("None");
    assertThat(invokeResolveInlineNoneValue("NoNe", false)).isEqualTo("None");
    assertThat(invokeResolveInlineNoneValue("なし", false)).isEqualTo("なし");
    assertThat(invokeResolveInlineNoneValue("unknown-token", true)).isEqualTo("なし");
    assertThat(invokeResolveInlineNoneValue("unknown-token", false)).isEqualTo("None");
  }

  @Test
  void isPresentPreconditionLine_shouldIgnoreBlankAndNoneMarkers() {
    assertThat(invokeIsPresentPreconditionLine(null)).isFalse();
    assertThat(invokeIsPresentPreconditionLine("  ")).isFalse();
    assertThat(invokeIsPresentPreconditionLine("- None")).isFalse();
    assertThat(invokeIsPresentPreconditionLine("- なし")).isFalse();
    assertThat(invokeIsPresentPreconditionLine("- userId != null")).isTrue();
  }

  @Test
  void containsConditionLine_shouldMatchNormalizedConditionToken() {
    String section = "- `userId` == \"A\"\n- amount > 0";

    assertThat(invokeContainsConditionLine(null, "userId==A")).isFalse();
    assertThat(invokeContainsConditionLine(section, null)).isFalse();
    assertThat(invokeContainsConditionLine(section, "   ")).isFalse();
    assertThat(invokeContainsConditionLine(section, "``'\"。、")).isFalse();
    assertThat(invokeContainsConditionLine(section, "userId == A")).isTrue();
    assertThat(invokeContainsConditionLine(section, "status == ACTIVE")).isFalse();
  }

  @Test
  void containsConditionInLines_shouldMatchNormalizedConditionToken() {
    List<String> lines = List.of("- `userId` == \"A\"", "- amount > 0");

    assertThat(invokeContainsConditionInLines(null, "userId==A")).isFalse();
    assertThat(invokeContainsConditionInLines(List.of(), "userId==A")).isFalse();
    assertThat(invokeContainsConditionInLines(lines, null)).isFalse();
    assertThat(invokeContainsConditionInLines(lines, "  ")).isFalse();
    assertThat(invokeContainsConditionInLines(lines, "``'\"。、")).isFalse();
    assertThat(invokeContainsConditionInLines(lines, "userId == A")).isTrue();
    assertThat(invokeContainsConditionInLines(lines, "status == ACTIVE")).isFalse();
  }

  @Test
  void hasMissingConditions_shouldDetectUncoveredSourceBackedConditions() {
    String section = "- `userId` == \"A\"\n- amount > 0";

    assertThat(invokeHasMissingConditions(section, null)).isFalse();
    assertThat(invokeHasMissingConditions(section, List.of())).isFalse();
    assertThat(invokeHasMissingConditions(section, List.of("userId == A", "amount > 0"))).isFalse();
    assertThat(invokeHasMissingConditions(section, List.of("userId == A", "status == ACTIVE")))
        .isTrue();
  }

  private boolean invokeContainsFailureLikeFlow(String section) {
    return invokeBoolean("containsFailureLikeFlow", new Class<?>[] {String.class}, section);
  }

  private String invokeNormalizeMatchToken(String value) {
    return (String) invoke("normalizeMatchToken", new Class<?>[] {String.class}, value);
  }

  private String invokeResolveInlineNoneValue(String token, boolean japanese) {
    return (String)
        invoke(
            "resolveInlineNoneValue",
            new Class<?>[] {String.class, boolean.class},
            token,
            japanese);
  }

  private boolean invokeIsPresentPreconditionLine(String line) {
    return invokeBoolean("isPresentPreconditionLine", new Class<?>[] {String.class}, line);
  }

  private boolean invokeContainsConditionLine(String section, String condition) {
    return invokeBoolean(
        "containsConditionLine", new Class<?>[] {String.class, String.class}, section, condition);
  }

  private boolean invokeContainsConditionInLines(List<String> lines, String condition) {
    return invokeBoolean(
        "containsConditionInLines", new Class<?>[] {List.class, String.class}, lines, condition);
  }

  private boolean invokeHasMissingConditions(String section, List<String> sourceBackedConditions) {
    return invokeBoolean(
        "hasMissingConditions",
        new Class<?>[] {String.class, List.class},
        section,
        sourceBackedConditions);
  }

  private boolean invokeBoolean(String methodName, Class<?>[] parameterTypes, Object... args) {
    return (Boolean) invoke(methodName, parameterTypes, args);
  }

  private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = LlmDocumentGenerator.class.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method.invoke(generator, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke " + methodName, e);
    }
  }

  private static final class NoopLlmClient implements LlmClientPort {
    @Override
    public String generateTest(String prompt, Config.LlmConfig llmConfig) {
      return "";
    }

    @Override
    public ProviderProfile profile() {
      return new ProviderProfile("noop", Set.of(), Optional.empty());
    }
  }
}
