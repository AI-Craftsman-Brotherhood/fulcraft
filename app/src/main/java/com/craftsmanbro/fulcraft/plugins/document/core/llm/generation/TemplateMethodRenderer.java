package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier.TemplateType;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders deterministic Markdown documentation for simple methods classified as template targets by
 * {@link MethodDocClassifier}.
 */
public final class TemplateMethodRenderer {

  private static final Pattern RETURN_PATTERN = Pattern.compile("\\breturn\\s+([^;]+);");

  private static final Pattern ASSIGNMENT_PATTERN =
      Pattern.compile("([A-Za-z0-9_$.]+)\\s*=\\s*([^;]+);");

  private final MethodDocClassifier classifier;

  public TemplateMethodRenderer(final MethodDocClassifier classifier) {
    this.classifier = classifier;
  }

  /**
   * Renders one method section in the canonical 3.x.1..3.x.7 format.
   *
   * @param method the method to render
   * @param methodIndex 1-based method index in the class
   * @param japanese true for Japanese, false for English
   * @return rendered section text
   */
  public String render(final MethodInfo method, final int methodIndex, final boolean japanese) {
    if (method == null) {
      return "";
    }
    final TemplateType type = classifier.identifyType(method);
    final String methodName = method.getName() != null ? method.getName().strip() : "unknown";
    final String signature =
        method.getSignature() != null ? method.getSignature().strip() : methodName + "()";
    final String returnExpression = extractReturnExpression(method);
    final String assignmentExpression = extractAssignmentExpression(method);
    final StringBuilder sb = new StringBuilder();
    sb.append("### 3.").append(methodIndex).append(" ").append(methodName).append("\n");
    appendSubsection(
        sb,
        methodIndex,
        1,
        japanese ? "入出力" : "Inputs/Outputs",
        bullet(japanese ? "シグネチャ" : "Signature", signature));
    appendSubsection(
        sb,
        methodIndex,
        2,
        japanese ? "事前条件" : "Preconditions",
        buildPreconditions(type, japanese));
    appendSubsection(
        sb,
        methodIndex,
        3,
        japanese ? "事後条件" : "Postconditions",
        buildPostconditions(type, japanese, returnExpression, assignmentExpression));
    appendSubsection(
        sb,
        methodIndex,
        4,
        japanese ? "正常フロー" : "Normal Flow",
        buildNormalFlow(type, methodName, japanese, returnExpression, assignmentExpression));
    appendSubsection(
        sb,
        methodIndex,
        5,
        japanese ? "異常・境界" : "Error/Boundary Handling",
        japanese
            ? "- ソースコード上に明示的な例外送出・境界専用分岐は確認されない。"
            : "- No explicit exception throw or boundary-only branch is observed in source.");
    appendSubsection(
        sb, methodIndex, 6, japanese ? "依存呼び出し" : "Dependencies", japanese ? "- なし" : "- None");
    appendSubsection(
        sb,
        methodIndex,
        7,
        japanese ? "テスト観点" : "Test Viewpoints",
        buildTestViewpoints(type, methodName, japanese, returnExpression, assignmentExpression));
    return sb.toString().stripTrailing();
  }

  private void appendSubsection(
      final StringBuilder sb,
      final int methodIndex,
      final int subsectionIndex,
      final String label,
      final String content) {
    sb.append("#### 3.")
        .append(methodIndex)
        .append(".")
        .append(subsectionIndex)
        .append(" ")
        .append(label)
        .append("\n");
    sb.append(content).append("\n\n");
  }

  private String buildPreconditions(final TemplateType type, final boolean japanese) {
    return switch (type) {
      case SETTER ->
          japanese
              ? "- 引数は null を許容し、値はそのまま格納される。"
              : "- Argument is accepted as-is (including null).";
      case EQUALS -> japanese ? "- 比較対象引数は null を許容する。" : "- Compared argument may be null.";
      default -> japanese ? "- なし" : "- None";
    };
  }

  private String buildPostconditions(
      final TemplateType type,
      final boolean japanese,
      final String returnExpression,
      final String assignmentExpression) {
    return switch (type) {
      case GETTER ->
          returnExpression.isBlank()
              ? (japanese ? "- 対応する値を返す。" : "- Returns the corresponding value.")
              : (japanese
                  ? "- `" + returnExpression + "` を直接返す。"
                  : "- Returns `" + returnExpression + "` directly.");
      case SETTER ->
          assignmentExpression.isBlank()
              ? (japanese
                  ? "- 引数値が内部状態へ反映される。"
                  : "- Argument value is reflected to internal state.")
              : (japanese
                  ? "- 代入 `" + assignmentExpression + "` が実行される。"
                  : "- Assignment `" + assignmentExpression + "` is executed.");
      case TO_STRING ->
          japanese ? "- オブジェクトの文字列表現を返す。" : "- Returns a string representation of the object.";
      case HASH_CODE ->
          japanese ? "- オブジェクト状態に基づくハッシュ値を返す。" : "- Returns a hash value based on object state.";
      case EQUALS ->
          japanese ? "- 比較結果を boolean として返す。" : "- Returns comparison result as boolean.";
      case UNKNOWN -> japanese ? "- 処理が完了する。" : "- Operation completes.";
    };
  }

  private String buildNormalFlow(
      final TemplateType type,
      final String methodName,
      final boolean japanese,
      final String returnExpression,
      final String assignmentExpression) {
    return switch (type) {
      case GETTER ->
          returnExpression.isBlank()
              ? (japanese
                  ? "- `" + methodName + "` を呼び出すと、保持値を返す。"
                  : "- Calling `" + methodName + "` returns the stored value.")
              : (japanese
                  ? "- `" + methodName + "` は `" + returnExpression + "` を返却する。"
                  : "- `" + methodName + "` returns `" + returnExpression + "`.");
      case SETTER ->
          assignmentExpression.isBlank()
              ? (japanese
                  ? "- `" + methodName + "` は引数値を内部状態へ反映する。"
                  : "- `" + methodName + "` reflects the argument into internal state.")
              : (japanese
                  ? "- `" + methodName + "` は `" + assignmentExpression + "` を実行する。"
                  : "- `" + methodName + "` executes `" + assignmentExpression + "`.");
      case TO_STRING ->
          japanese
              ? "- `toString` の実装に従い文字列を返却する。"
              : "- Returns a string according to the `toString` implementation.";
      case HASH_CODE ->
          japanese
              ? "- `hashCode` の実装に従いハッシュ値を返却する。"
              : "- Returns a hash according to the `hashCode` implementation.";
      case EQUALS ->
          japanese
              ? "- `equals` の実装に従い等価性を判定して返却する。"
              : "- Evaluates equality according to the `equals` implementation.";
      case UNKNOWN ->
          japanese
              ? "- `" + methodName + "` の実装を順に実行する。"
              : "- Executes the `" + methodName + "` implementation.";
    };
  }

  private String buildTestViewpoints(
      final TemplateType type,
      final String methodName,
      final boolean japanese,
      final String returnExpression,
      final String assignmentExpression) {
    return switch (type) {
      case GETTER ->
          returnExpression.isBlank()
              ? (japanese
                  ? "- `" + methodName + "` の返却値が期待値と一致すること。"
                  : "- Verify that `" + methodName + "` return value matches expectation.")
              : (japanese
                  ? "- `" + methodName + "` が `" + returnExpression + "` を返却すること。"
                  : "- Verify that `" + methodName + "` returns `" + returnExpression + "`.");
      case SETTER ->
          assignmentExpression.isBlank()
              ? (japanese
                  ? "- `" + methodName + "` 実行後に内部状態が更新されること。"
                  : "- Verify that internal state is updated after `" + methodName + "`.")
              : (japanese
                  ? "- `" + assignmentExpression + "` の代入結果を検証する。"
                  : "- Verify assignment outcome of `" + assignmentExpression + "`.");
      case TO_STRING ->
          japanese
              ? "- `toString` が null ではない文字列を返却すること。"
              : "- Verify that `toString` returns a non-null string.";
      case HASH_CODE ->
          japanese
              ? "- 同値オブジェクトで同一ハッシュ値となること。"
              : "- Verify that equal objects produce the same hash value.";
      case EQUALS ->
          japanese
              ? "- 同値・非同値・null 比較の結果を検証する。"
              : "- Verify equality results for equal, unequal, and null comparisons.";
      case UNKNOWN ->
          japanese
              ? "- `" + methodName + "` の動作を検証する。"
              : "- Verify behavior of `" + methodName + "`.";
    };
  }

  private String bullet(final String label, final String value) {
    return "- " + label + ": `" + value + "`";
  }

  private String extractReturnExpression(final MethodInfo method) {
    final String source =
        method == null || method.getSourceCode() == null ? "" : method.getSourceCode();
    final Matcher matcher = RETURN_PATTERN.matcher(source);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1).trim();
  }

  private String extractAssignmentExpression(final MethodInfo method) {
    final String source =
        method == null || method.getSourceCode() == null ? "" : method.getSourceCode();
    final Matcher matcher = ASSIGNMENT_PATTERN.matcher(source);
    if (!matcher.find()) {
      return "";
    }
    final String left = matcher.group(1).trim();
    final String right = matcher.group(2).trim();
    if (left.isBlank() || right.isBlank()) {
      return "";
    }
    final String lowerLeft = left.toLowerCase(Locale.ROOT);
    if ("return".equals(lowerLeft)) {
      return "";
    }
    return left + " = " + right;
  }
}
