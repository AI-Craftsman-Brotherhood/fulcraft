package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.util.Locale;
import java.util.Set;

/** Determines constructor-related source semantics used by fallback document generation. */
public final class LlmConstructorSemantics {

  private static final Set<String> CONSTRUCTOR_ALLOWED_MODIFIERS =
      Set.of("public", "protected", "private");

  public boolean isTrivialEmptyConstructor(final MethodInfo method) {
    if (!isConstructor(method)) {
      return false;
    }
    final String body = extractMethodBody(method == null ? null : method.getSourceCode());
    if (body.isBlank()) {
      return true;
    }
    for (final String rawStatement : body.split(";")) {
      final String statement = rawStatement == null ? "" : rawStatement.strip();
      if (statement.isBlank() || isIgnorableSourceStatement(statement)) {
        continue;
      }
      return false;
    }
    return true;
  }

  public boolean isConstructor(final MethodInfo method) {
    if (method == null) {
      return false;
    }
    final String methodName = normalizeSimpleName(method.getName());
    if (methodName.isBlank()) {
      return false;
    }
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final String normalized = signature.strip().replace('$', '.').replace("`", "");
    final int paren = normalized.indexOf('(');
    if (paren <= 0) {
      return false;
    }
    final String head = normalized.substring(0, paren).trim();
    if (head.isBlank()) {
      return false;
    }
    final String[] tokens = head.split("\\s+");
    if (tokens.length == 0) {
      return false;
    }
    final String nameToken = normalizeSimpleName(tokens[tokens.length - 1]);
    if (!methodName.equals(nameToken)) {
      return false;
    }
    boolean inTypeParameterClause = false;
    for (int i = 0; i < tokens.length - 1; i++) {
      final String token = tokens[i] == null ? "" : tokens[i].trim();
      if (token.isBlank()) {
        continue;
      }
      if (token.startsWith("@")) {
        continue;
      }
      if (token.startsWith("<")) {
        inTypeParameterClause = true;
        if (token.endsWith(">")) {
          inTypeParameterClause = false;
        }
        continue;
      }
      if (inTypeParameterClause) {
        if (token.endsWith(">")) {
          inTypeParameterClause = false;
        }
        continue;
      }
      if (CONSTRUCTOR_ALLOWED_MODIFIERS.contains(token.toLowerCase(Locale.ROOT))) {
        continue;
      }
      return false;
    }
    return true;
  }

  private String extractMethodBody(final String sourceCode) {
    final String normalized = DocumentUtils.stripCommentedRegions(sourceCode);
    if (normalized == null || normalized.isBlank()) {
      return "";
    }
    final int openBrace = normalized.indexOf('{');
    final int closeBrace = normalized.lastIndexOf('}');
    if (openBrace >= 0 && closeBrace > openBrace) {
      return normalized.substring(openBrace + 1, closeBrace).strip();
    }
    return normalized.strip();
  }

  private boolean isIgnorableSourceStatement(final String statement) {
    final String normalized = LlmDocumentTextUtils.normalizeLine(statement);
    if (normalized.isBlank()) {
      return true;
    }
    return normalized.startsWith("super(")
        || normalized.startsWith("this(")
        || normalized.startsWith("objects.requirenonnull(")
        || normalized.startsWith("preconditions.checknotnull(")
        || normalized.startsWith("preconditions.checkargument(")
        || normalized.startsWith("assert ");
  }

  private String normalizeSimpleName(final String token) {
    if (token == null || token.isBlank()) {
      return "";
    }
    String normalized = token.strip();
    final int dotIndex = normalized.lastIndexOf('.');
    if (dotIndex >= 0 && dotIndex + 1 < normalized.length()) {
      normalized = normalized.substring(dotIndex + 1);
    }
    return normalized;
  }
}
