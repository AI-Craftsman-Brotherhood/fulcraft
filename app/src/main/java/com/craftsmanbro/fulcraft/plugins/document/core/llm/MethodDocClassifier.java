package com.craftsmanbro.fulcraft.plugins.document.core.llm;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.Locale;

/**
 * Classifies methods as "template targets" (simple getters/setters/canonical Object methods) that
 * can be documented deterministically without LLM, or "LLM targets" that require LLM-based
 * generation.
 *
 * <p>Classification is intentionally conservative: if there is any doubt, the method is NOT
 * classified as a template target, ensuring that complex logic is always handled by the LLM.
 */
public final class MethodDocClassifier {

  /** Maximum LOC for a method to be considered trivially simple. */
  private static final int TRIVIAL_LOC_THRESHOLD = 4;

  /**
   * Returns {@code true} if the method is simple enough to be documented using a deterministic
   * template rather than LLM generation.
   *
   * @param method the method to classify
   * @param classSimpleName the simple name of the enclosing class (for constructor detection)
   * @return true if the method is a template target
   */
  public boolean isTemplateTarget(final MethodInfo method, final String classSimpleName) {
    if (method == null || classSimpleName == null || classSimpleName.isBlank()) {
      return false;
    }
    // Constructors are never template targets
    if (isConstructor(method, classSimpleName)) {
      return false;
    }
    // Methods exceeding LOC threshold or showing behavioral signals should go to
    // LLM
    if (method.getLoc() > TRIVIAL_LOC_THRESHOLD) {
      return false;
    }
    if (hasBehavioralSignals(method)) {
      return false;
    }
    // Check if the name matches a known simple pattern
    final String rawName = method.getName() != null ? method.getName().strip() : "";
    final String normalizedName = rawName.toLowerCase(Locale.ROOT);
    if (normalizedName.isBlank()) {
      return false;
    }
    return isSimpleAccessorOrCanonicalMethod(rawName, normalizedName, method.getParameterCount());
  }

  /**
   * Identifies the template type for a method that has already been classified as a template
   * target.
   *
   * @param method the method (must be a template target)
   * @return the template type, or UNKNOWN if not classifiable
   */
  public TemplateType identifyType(final MethodInfo method) {
    if (method == null) {
      return TemplateType.UNKNOWN;
    }
    final String rawName = method.getName() != null ? method.getName().strip() : "";
    final String normalizedName = rawName.toLowerCase(Locale.ROOT);
    if (normalizedName.isBlank()) {
      return TemplateType.UNKNOWN;
    }
    if ("tostring".equals(normalizedName)) {
      return TemplateType.TO_STRING;
    }
    if ("hashcode".equals(normalizedName)) {
      return TemplateType.HASH_CODE;
    }
    if ("equals".equals(normalizedName)) {
      return TemplateType.EQUALS;
    }
    if (hasGetterPrefix(rawName, normalizedName)) {
      return TemplateType.GETTER;
    }
    if (hasSetterPrefix(rawName, normalizedName)) {
      return TemplateType.SETTER;
    }
    return TemplateType.UNKNOWN;
  }

  private boolean isSimpleAccessorOrCanonicalMethod(
      final String rawName, final String normalizedName, final int parameterCount) {
    // Getter-like: get*, is*, has* with 0 parameters
    if (hasGetterPrefix(rawName, normalizedName) && parameterCount == 0) {
      return true;
    }
    // Setter-like: set* with 0 or 1 parameters
    if (hasSetterPrefix(rawName, normalizedName) && parameterCount <= 1) {
      return true;
    }
    // Canonical Object methods
    if (("tostring".equals(normalizedName) || "hashcode".equals(normalizedName))
        && parameterCount == 0) {
      return true;
    }
    return "equals".equals(normalizedName) && parameterCount == 1;
  }

  private boolean hasGetterPrefix(final String rawName, final String normalizedName) {
    return (normalizedName.startsWith("get")
            && normalizedName.length() > 3
            && Character.isUpperCase(rawName.charAt(3)))
        || (normalizedName.startsWith("is")
            && normalizedName.length() > 2
            && Character.isUpperCase(rawName.charAt(2)))
        || (normalizedName.startsWith("has")
            && normalizedName.length() > 3
            && Character.isUpperCase(rawName.charAt(3)));
  }

  private boolean hasSetterPrefix(final String rawName, final String normalizedName) {
    return normalizedName.startsWith("set")
        && normalizedName.length() > 3
        && Character.isUpperCase(rawName.charAt(3));
  }

  private boolean isConstructor(final MethodInfo method, final String classSimpleName) {
    if (classSimpleName.equals(method.getName())) {
      return true;
    }
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final String normalized = signature.strip().replace('$', '.');
    final int parenIndex = normalized.indexOf('(');
    if (parenIndex <= 0) {
      return false;
    }
    String candidate = normalized.substring(0, parenIndex).trim();
    final int lastSpace = candidate.lastIndexOf(' ');
    if (lastSpace >= 0) {
      candidate = candidate.substring(lastSpace + 1);
    }
    final int lastDot = candidate.lastIndexOf('.');
    if (lastDot >= 0) {
      candidate = candidate.substring(lastDot + 1);
    }
    return classSimpleName.equals(candidate);
  }

  /**
   * Conservative check for behavioral signals. If any signal is present, the method should NOT be a
   * template target.
   */
  private boolean hasBehavioralSignals(final MethodInfo method) {
    if (method.getCyclomaticComplexity() > 1 || method.hasConditionals() || method.hasLoops()) {
      return true;
    }
    if (method.getBranchSummary() != null
        && (!method.getBranchSummary().getGuards().isEmpty()
            || !method.getBranchSummary().getSwitches().isEmpty()
            || !method.getBranchSummary().getPredicates().isEmpty())) {
      return true;
    }
    return !method.getThrownExceptions().isEmpty();
  }

  /** Enumeration of template types for simple methods. */
  public enum TemplateType {
    GETTER,
    SETTER,
    TO_STRING,
    HASH_CODE,
    EQUALS,
    UNKNOWN
  }
}
