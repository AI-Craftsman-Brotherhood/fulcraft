package com.craftsmanbro.fulcraft.plugins.document.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.GuardSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MarkdownDocumentGeneratorBranchCoverageTest {

  private final MarkdownDocumentGenerator generator = new MarkdownDocumentGenerator();

  @BeforeAll
  static void setUpLocale() {
    MessageSource.setLocale(Locale.JAPANESE);
  }

  @AfterAll
  static void resetLocale() {
    MessageSource.initialize();
  }

  @Test
  void resolvePathType_shouldClassifyUsingExpectedHintAndDescription() {
    String emptyLabel = MessageSource.getMessage("document.value.empty");

    assertThat(invokeResolvePathType(null)).isEqualTo(emptyLabel);

    RepresentativePath path = new RepresentativePath();
    path.setExpectedOutcomeHint("  custom-type  ");
    path.setDescription("ignored");
    assertThat(invokeResolvePathType(path)).isEqualTo("custom-type");

    path.setExpectedOutcomeHint(" ");
    path.setDescription(null);
    assertThat(invokeResolvePathType(path)).isEqualTo(emptyLabel);

    path.setDescription("boundary condition path");
    assertThat(invokeResolvePathType(path)).isEqualTo("boundary");

    path.setDescription("EARLY RETURN path");
    assertThat(invokeResolvePathType(path)).isEqualTo("early-return");

    path.setDescription("success scenario");
    assertThat(invokeResolvePathType(path)).isEqualTo("success");

    path.setDescription("neutral narrative");
    assertThat(invokeResolvePathType(path)).isEqualTo(emptyLabel);
  }

  @Test
  void formatBrittlenessSignals_shouldHandleNullEmptyAndNullElements() {
    String emptyLabel = MessageSource.getMessage("document.value.empty");

    assertThat(invokeFormatBrittlenessSignals(null)).isEqualTo(emptyLabel);
    assertThat(invokeFormatBrittlenessSignals(List.of())).isEqualTo(emptyLabel);

    List<BrittlenessSignal> mixed = new ArrayList<>();
    mixed.add(null);
    mixed.add(BrittlenessSignal.TIME);
    mixed.add(BrittlenessSignal.IO);
    assertThat(invokeFormatBrittlenessSignals(mixed)).isEqualTo("time, io");

    List<BrittlenessSignal> allNull = new ArrayList<>();
    allNull.add(null);
    assertThat(invokeFormatBrittlenessSignals(allNull)).isEqualTo(emptyLabel);
  }

  @Test
  void appendClassBadges_shouldRenderAttributesOnlyWhenAnyBadgeExists() {
    String attributesLabel = MessageSource.getMessage("document.label.attributes");
    String nestedBadge = MessageSource.getMessage("document.badge.nested");
    String anonymousBadge = MessageSource.getMessage("document.badge.anonymous");
    String hasInnerBadge = MessageSource.getMessage("document.badge.has_inner");

    StringBuilder plainOutput = new StringBuilder();
    ClassInfo plainClass = new ClassInfo();
    plainClass.setFqn("com.example.Plain");
    invokeAppendClassBadges(plainOutput, plainClass);
    assertThat(plainOutput.toString()).doesNotContain(attributesLabel);

    StringBuilder attributedOutput = new StringBuilder();
    ClassInfo attributedClass = new ClassInfo();
    attributedClass.setFqn("com.example.Attributed");
    attributedClass.setNestedClass(true);
    attributedClass.setAnonymous(true);
    attributedClass.setHasNestedClasses(true);
    invokeAppendClassBadges(attributedOutput, attributedClass);

    assertThat(attributedOutput.toString()).contains(attributesLabel);
    assertThat(attributedOutput.toString()).contains(nestedBadge);
    assertThat(attributedOutput.toString()).contains(anonymousBadge);
    assertThat(attributedOutput.toString()).contains(hasInnerBadge);
  }

  @Test
  void appendStructureIndicators_shouldSupportLoopAndConditionalCombinations() {
    String structureLabel = MessageSource.getMessage("document.label.structure");
    String loopLabel = MessageSource.getMessage("document.structure.loop");
    String conditionalLabel = MessageSource.getMessage("document.structure.conditional");

    StringBuilder emptyOutput = new StringBuilder();
    MethodInfo noStructure = new MethodInfo();
    invokeAppendStructureIndicators(emptyOutput, noStructure);
    assertThat(emptyOutput.toString()).isEmpty();

    StringBuilder loopOnlyOutput = new StringBuilder();
    MethodInfo loopOnly = new MethodInfo();
    loopOnly.setHasLoops(true);
    invokeAppendStructureIndicators(loopOnlyOutput, loopOnly);
    assertThat(loopOnlyOutput.toString()).contains(structureLabel);
    assertThat(loopOnlyOutput.toString()).contains(loopLabel);
    assertThat(loopOnlyOutput.toString()).doesNotContain(conditionalLabel);

    StringBuilder conditionalOnlyOutput = new StringBuilder();
    MethodInfo conditionalOnly = new MethodInfo();
    conditionalOnly.setHasConditionals(true);
    invokeAppendStructureIndicators(conditionalOnlyOutput, conditionalOnly);
    assertThat(conditionalOnlyOutput.toString()).contains(structureLabel);
    assertThat(conditionalOnlyOutput.toString()).doesNotContain(loopLabel);
    assertThat(conditionalOnlyOutput.toString()).contains(conditionalLabel);

    StringBuilder bothOutput = new StringBuilder();
    MethodInfo both = new MethodInfo();
    both.setHasLoops(true);
    both.setHasConditionals(true);
    invokeAppendStructureIndicators(bothOutput, both);
    assertThat(bothOutput.toString()).contains(loopLabel);
    assertThat(bothOutput.toString()).contains(conditionalLabel);
  }

  @Test
  void appendMethodAnnotations_shouldSkipEmptyAndRenderPopulatedAnnotations() {
    StringBuilder emptyOutput = new StringBuilder();
    MethodInfo emptyAnnotationsMethod = new MethodInfo();
    invokeAppendMethodAnnotations(emptyOutput, emptyAnnotationsMethod);
    assertThat(emptyOutput.toString()).isEmpty();

    StringBuilder populatedOutput = new StringBuilder();
    MethodInfo annotatedMethod = new MethodInfo();
    annotatedMethod.setAnnotations(List.of("Transactional", "Nullable"));
    invokeAppendMethodAnnotations(populatedOutput, annotatedMethod);

    assertThat(populatedOutput.toString()).contains("`@Transactional`");
    assertThat(populatedOutput.toString()).contains("`@Nullable`");
  }

  @Test
  void buildAnchorId_shouldFallbackForBlankSlugAndSuffixDuplicates() {
    Map<String, Integer> counts = new HashMap<>();

    assertThat(invokeBuildAnchorId("process(String id)", counts, 0)).isEqualTo("process-string-id");
    assertThat(invokeBuildAnchorId("process(String id)", counts, 1))
        .isEqualTo("process-string-id-2");
    assertThat(invokeBuildAnchorId("!!!", counts, 7)).isEqualTo("method-7");
    assertThat(invokeBuildAnchorId(null, counts, 8)).isEqualTo("method-8");
  }

  @Test
  void stripGenericTypes_shouldHandleNestedAndNonReplacingPatterns() {
    assertThat(
            invokeStripGenericTypes(
                "java.util.Map<java.lang.String, java.util.List<com.example.Type>>"))
        .isEqualTo("java.util.Map");
    assertThat(invokeStripGenericTypes("List<String>")).isEqualTo("List");
    assertThat(invokeStripGenericTypes("><")).isEqualTo("><");
    assertThat(invokeStripGenericTypes("PlainType")).isEqualTo("PlainType");
  }

  @Test
  void summaryHelpers_shouldDetectEmptyBranchSummariesAndListContent() {
    BranchSummary emptySummary = new BranchSummary();
    assertThat(invokeIsBranchSummaryEmpty(emptySummary)).isTrue();

    GuardSummary guard = new GuardSummary();
    guard.setCondition("id != null");
    BranchSummary guardedSummary = new BranchSummary();
    guardedSummary.setGuards(List.of(guard));
    assertThat(invokeIsBranchSummaryEmpty(guardedSummary)).isFalse();

    assertThat(invokeHasItems(null)).isFalse();
    assertThat(invokeHasItems(List.of())).isFalse();
    assertThat(invokeHasItems(List.of("item"))).isTrue();
  }

  @Test
  void nullSafeAndNormalizeCodeBlock_shouldHandleFallbackAndFenceEscaping() {
    String naLabel = MessageSource.getMessage("document.value.na");

    assertThat(invokeNullSafe(null)).isEqualTo(naLabel);
    assertThat(invokeNullSafe(" ")).isEqualTo(naLabel);
    assertThat(invokeNullSafe("value")).isEqualTo("value");

    assertThat(invokeNormalizeCodeBlock(null)).isNull();
    assertThat(invokeNormalizeCodeBlock("  ")).isNull();
    assertThat(invokeNormalizeCodeBlock("```java\nreturn 1;\n```")).contains("``\\`java");
  }

  private String invokeResolvePathType(RepresentativePath path) {
    return (String) invoke("resolvePathType", new Class<?>[] {RepresentativePath.class}, path);
  }

  private String invokeFormatBrittlenessSignals(List<BrittlenessSignal> signals) {
    return (String) invoke("formatBrittlenessSignals", new Class<?>[] {List.class}, signals);
  }

  private void invokeAppendClassBadges(StringBuilder sb, ClassInfo classInfo) {
    invoke(
        "appendClassBadges", new Class<?>[] {StringBuilder.class, ClassInfo.class}, sb, classInfo);
  }

  private void invokeAppendStructureIndicators(StringBuilder sb, MethodInfo method) {
    invoke(
        "appendStructureIndicators",
        new Class<?>[] {StringBuilder.class, MethodInfo.class},
        sb,
        method);
  }

  private void invokeAppendMethodAnnotations(StringBuilder sb, MethodInfo method) {
    invoke(
        "appendMethodAnnotations",
        new Class<?>[] {StringBuilder.class, MethodInfo.class},
        sb,
        method);
  }

  private String invokeBuildAnchorId(String anchorText, Map<String, Integer> counts, int index) {
    return (String)
        invoke(
            "buildAnchorId",
            new Class<?>[] {String.class, Map.class, int.class},
            anchorText,
            counts,
            index);
  }

  private String invokeStripGenericTypes(String value) {
    return (String) invoke("stripGenericTypes", new Class<?>[] {String.class}, value);
  }

  private boolean invokeIsBranchSummaryEmpty(BranchSummary summary) {
    return (Boolean) invoke("isBranchSummaryEmpty", new Class<?>[] {BranchSummary.class}, summary);
  }

  private boolean invokeHasItems(List<?> values) {
    return (Boolean) invoke("hasItems", new Class<?>[] {List.class}, values);
  }

  private String invokeNullSafe(String value) {
    return (String) invoke("nullSafe", new Class<?>[] {String.class}, value);
  }

  private String invokeNormalizeCodeBlock(String value) {
    return (String) invoke("normalizeCodeBlock", new Class<?>[] {String.class}, value);
  }

  private Object invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      Method method = MarkdownDocumentGenerator.class.getDeclaredMethod(methodName, parameterTypes);
      method.setAccessible(true);
      return method.invoke(generator, args);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Failed to invoke " + methodName, e);
    }
  }
}
