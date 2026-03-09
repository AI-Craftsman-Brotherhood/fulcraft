package com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmMethodFlowFactsExtractorTest {

  private final LlmMethodFlowFactsExtractor extractor = new LlmMethodFlowFactsExtractor();

  @Test
  void isEarlyReturnIncompatible_shouldReturnTrueWhenSourceThrowsWithoutReturn() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
                        public String resolve(String value) {
                            if (value == null) {
                                throw new IllegalArgumentException("value");
                            }
                            throw new IllegalStateException("unsupported");
                        }
                        """);

    assertThat(extractor.isEarlyReturnIncompatible(method)).isTrue();
  }

  @Test
  void isEarlyReturnIncompatible_shouldReturnFalseWhenReturnExistsOrThrowIsCommented() {
    MethodInfo withReturn = new MethodInfo();
    withReturn.setSourceCode(
        """
                        public String resolve(String value) {
                            if (value == null) {
                                throw new IllegalArgumentException("value");
                            }
                            return value.trim();
                        }
                        """);
    MethodInfo commentOnlyThrow = new MethodInfo();
    commentOnlyThrow.setSourceCode(
        """
                        public String resolve(String value) {
                            // throw new IllegalStateException("comment only");
                            return value;
                        }
                        """);

    assertThat(extractor.isEarlyReturnIncompatible(withReturn)).isFalse();
    assertThat(extractor.isEarlyReturnIncompatible(commentOnlyThrow)).isFalse();
  }

  @Test
  void collectSwitchCaseFacts_shouldExtractCaseFactsAndDeduplicateByExpressionAndLabel() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
                        public String classify(String status) {
                            switch (status) {
                                case "NEW", "PENDING" -> {
                                    return "active";
                                }
                                case 'E' -> {
                                    return "error";
                                }
                                default -> {
                                    return "other";
                                }
                            }

                            switch (status) {
                                case "new" -> {
                                    return "duplicate";
                                }
                                default -> {
                                    return "duplicate-default";
                                }
                            }
                        }
                        """);

    List<LlmMethodFlowFactsExtractor.SwitchCaseFact> facts =
        extractor.collectSwitchCaseFacts(method);

    assertThat(facts)
        .containsExactly(
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-status-new", "Switch case status=\"NEW\"", "case-\"NEW\""),
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-status-pending", "Switch case status=\"PENDING\"", "case-\"PENDING\""),
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-status-e", "Switch case status=\"E\"", "case-\"E\""),
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-status-default", "Switch default status", "default"));
  }

  @Test
  void collectSwitchCaseFacts_shouldIgnoreMalformedSwitchBlocks() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
                        public String classify(String value) {
                            switch (value) {
                                case "A" -> {
                                    return "a";
                                }
                        """);

    assertThat(extractor.collectSwitchCaseFacts(method)).isEmpty();
  }

  @Test
  void collectSwitchCaseFacts_shouldNormalizeExpressionAndCaseTokens() {
    MethodInfo method = new MethodInfo();
    method.setSourceCode(
        """
                        public String classify(State state) {
                            switch (state.currentStatus) {
                                case "`INIT`", 'E': {
                                    return "init";
                                }
                                default: {
                                    return "other";
                                }
                            }
                        }
                        """);

    List<LlmMethodFlowFactsExtractor.SwitchCaseFact> facts =
        extractor.collectSwitchCaseFacts(method);

    assertThat(facts)
        .containsExactly(
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-currentstatus-init", "Switch case currentStatus=\"INIT\"", "case-\"INIT\""),
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-currentstatus-e", "Switch case currentStatus=\"E\"", "case-\"E\""),
            new LlmMethodFlowFactsExtractor.SwitchCaseFact(
                "switch-currentstatus-default", "Switch default currentStatus", "default"));
  }

  @Test
  void isEarlyReturnIncompatible_shouldReturnFalseForNullMethodOrBlankSource() {
    MethodInfo blankSource = new MethodInfo();
    blankSource.setSourceCode("   ");

    assertThat(extractor.isEarlyReturnIncompatible(null)).isFalse();
    assertThat(extractor.isEarlyReturnIncompatible(blankSource)).isFalse();
    assertThat(extractor.collectSwitchCaseFacts(null)).isEmpty();
  }

  @Test
  void privateHelpers_shouldCoverSwitchNormalizationAndCaseLabelBranches() throws Exception {
    assertThat(invokeString("normalizeSwitchExpression", null)).isEqualTo("value");
    assertThat(invokeString("normalizeSwitchExpression", "   ")).isEqualTo("value");
    assertThat(invokeString("normalizeSwitchExpression", "order.status")).isEqualTo("status");
    assertThat(invokeString("normalizeSwitchExpression", "order.")).isEqualTo("order.");
    assertThat(invokeString("normalizeSwitchExpression", "status")).isEqualTo("status");

    assertThat(invokeString("normalizeCaseLabel", "\"A\"")).isEqualTo("A");
    assertThat(invokeString("normalizeCaseLabel", "'B'")).isEqualTo("B");
    assertThat(invokeString("normalizeCaseLabel", "`C`")).isEqualTo("C");
    assertThat(invokeString("normalizeCaseLabel", "\"")).isEqualTo("\"");
    assertThat(invokeString("normalizeCaseLabel", "   ")).isEmpty();
  }

  @Test
  void privateHelpers_shouldCoverBraceParserAndSanitizeBranches() throws Exception {
    assertThat(invokeInt("findMatchingClosingBrace", null, 0)).isEqualTo(-1);
    assertThat(invokeInt("findMatchingClosingBrace", "{}", -1)).isEqualTo(-1);
    assertThat(invokeInt("findMatchingClosingBrace", "{}", 2)).isEqualTo(-1);
    assertThat(invokeInt("findMatchingClosingBrace", "text", 0)).isEqualTo(-1);
    assertThat(invokeInt("findMatchingClosingBrace", "{a{b}c}", 0)).isEqualTo(6);
    assertThat(invokeInt("findMatchingClosingBrace", "{a{b}", 0)).isEqualTo(-1);

    assertThat(invokeString("sanitizeToken", null)).isEqualTo("value");
    assertThat(invokeString("sanitizeToken", "   ")).isEqualTo("value");
    assertThat(invokeString("sanitizeToken", "!!!")).isEqualTo("value");
    assertThat(invokeString("sanitizeToken", "Order.Status")).isEqualTo("order-status");
  }

  @Test
  void privateHelpers_shouldCoverAddCaseFactsAndStripSourceBranches() throws Exception {
    assertThat(invokeStripSource(null)).isEmpty();

    MethodInfo noSource = new MethodInfo();
    assertThat(invokeStripSource(noSource)).isEmpty();

    MethodInfo withComment = new MethodInfo();
    withComment.setSourceCode("/*comment*/ switch (status) { case \"A\" -> {} }");
    assertThat(invokeStripSource(withComment)).contains("switch");

    Map<String, LlmMethodFlowFactsExtractor.SwitchCaseFact> deduplicated = new LinkedHashMap<>();
    invokeAddCaseFacts(deduplicated, "status", null);
    invokeAddCaseFacts(deduplicated, "status", "   ");
    assertThat(deduplicated).isEmpty();

    invokeAddCaseFacts(deduplicated, "status", "case \"\" -> { return; }");
    assertThat(deduplicated).isEmpty();

    invokeAddCaseFacts(deduplicated, "status", "case \"A\" -> { return; }");
    assertThat(deduplicated).containsKey("status::a");

    invokeAddCaseFacts(deduplicated, "status", "default -> { return; }");
    assertThat(deduplicated).containsKey("status::default");
  }

  private String invokeString(String methodName, String value) throws Exception {
    Method method = LlmMethodFlowFactsExtractor.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (String) method.invoke(extractor, value);
  }

  private int invokeInt(String methodName, String sourceCode, int openBraceIndex) throws Exception {
    Method method =
        LlmMethodFlowFactsExtractor.class.getDeclaredMethod(methodName, String.class, int.class);
    method.setAccessible(true);
    return (int) method.invoke(extractor, sourceCode, openBraceIndex);
  }

  private void invokeAddCaseFacts(
      Map<String, LlmMethodFlowFactsExtractor.SwitchCaseFact> deduplicated,
      String switchExpression,
      String switchBody)
      throws Exception {
    Method method =
        LlmMethodFlowFactsExtractor.class.getDeclaredMethod(
            "addCaseFacts", Map.class, String.class, String.class);
    method.setAccessible(true);
    method.invoke(extractor, deduplicated, switchExpression, switchBody);
  }

  private String invokeStripSource(MethodInfo methodInfo) throws Exception {
    Method method =
        LlmMethodFlowFactsExtractor.class.getDeclaredMethod("stripSource", MethodInfo.class);
    method.setAccessible(true);
    return (String) method.invoke(extractor, methodInfo);
  }
}
