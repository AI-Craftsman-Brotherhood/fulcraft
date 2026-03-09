package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Composes class-specific purpose lines for fallback detailed-design documents. */
public final class LlmFallbackPurposeComposer {

  public List<String> composePurposeLines(
      final ClassInfo classInfo, final List<MethodInfo> methods, final boolean japanese) {
    if (classInfo == null) {
      return List.of();
    }
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final List<String> lines = new ArrayList<>();
    final String role = resolveRoleLabel(classInfo, methods, japanese);
    lines.add(
        localized(
            japanese,
            "document.llm.fallback.purpose.role_line.ja",
            "document.llm.fallback.purpose.role_line.en",
            className,
            role));
    final String operationLine = buildOperationFactLine(classInfo, methods, japanese);
    if (!operationLine.isBlank()) {
      lines.add(operationLine);
    }
    return lines;
  }

  private String resolveRoleLabel(
      final ClassInfo classInfo, final List<MethodInfo> methods, final boolean japanese) {
    final String simpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String normalizedName = simpleName == null ? "" : simpleName.toLowerCase(Locale.ROOT);
    if (normalizedName.endsWith("service")) {
      return localized(
          japanese,
          "document.llm.fallback.purpose.role.service.ja",
          "document.llm.fallback.purpose.role.service.en");
    }
    if (normalizedName.endsWith("repository")) {
      return localized(
          japanese,
          "document.llm.fallback.purpose.role.repository.ja",
          "document.llm.fallback.purpose.role.repository.en");
    }
    if (normalizedName.endsWith("util") || normalizedName.endsWith("utils")) {
      return localized(
          japanese,
          "document.llm.fallback.purpose.role.utility.ja",
          "document.llm.fallback.purpose.role.utility.en");
    }
    if (isLikelyDataHolder(classInfo, methods)) {
      return localized(
          japanese,
          "document.llm.fallback.purpose.role.data_holder.ja",
          "document.llm.fallback.purpose.role.data_holder.en");
    }
    return localized(
        japanese,
        "document.llm.fallback.purpose.role.domain.ja",
        "document.llm.fallback.purpose.role.domain.en");
  }

  private boolean isLikelyDataHolder(final ClassInfo classInfo, final List<MethodInfo> methods) {
    if (classInfo == null
        || classInfo.getFields().isEmpty()
        || methods == null
        || methods.isEmpty()) {
      return false;
    }
    int accessorLikeCount = 0;
    int evaluatedCount = 0;
    for (final MethodInfo method : methods) {
      if (method == null || method.getName() == null || method.getName().isBlank()) {
        continue;
      }
      final String name = method.getName().strip();
      if (isConstructorLike(name)) {
        continue;
      }
      evaluatedCount++;
      if (isAccessorLike(name)) {
        accessorLikeCount++;
      }
    }
    if (evaluatedCount == 0) {
      return true;
    }
    return accessorLikeCount * 100 / evaluatedCount >= 70;
  }

  private String buildOperationFactLine(
      final ClassInfo classInfo, final List<MethodInfo> methods, final boolean japanese) {
    final List<String> operationNames = collectPrincipalOperationNames(classInfo, methods);
    if (!operationNames.isEmpty()) {
      final String listed = "`" + String.join("`, `", operationNames) + "`";
      return localized(
          japanese,
          "document.llm.fallback.purpose.operation_line.ja",
          "document.llm.fallback.purpose.operation_line.en",
          listed);
    }
    final List<String> fieldNames = collectPrincipalFieldNames(classInfo);
    if (!fieldNames.isEmpty()) {
      final String listed = "`" + String.join("`, `", fieldNames) + "`";
      return localized(
          japanese,
          "document.llm.fallback.purpose.field_line.ja",
          "document.llm.fallback.purpose.field_line.en",
          listed);
    }
    final int methodCount = methods == null ? 0 : methods.size();
    return localized(
        japanese,
        "document.llm.fallback.purpose.method_count_line.ja",
        "document.llm.fallback.purpose.method_count_line.en",
        methodCount);
  }

  private List<String> collectPrincipalOperationNames(
      final ClassInfo classInfo, final List<MethodInfo> methods) {
    if (methods == null || methods.isEmpty()) {
      return List.of();
    }
    final String classSimpleName =
        classInfo == null
            ? ""
            : DocumentUtils.getSimpleName(classInfo.getFqn()).toLowerCase(Locale.ROOT);
    final Set<String> names = new LinkedHashSet<>();
    for (final MethodInfo method : methods) {
      if (method == null || method.getName() == null || method.getName().isBlank()) {
        continue;
      }
      final String name = method.getName().strip();
      final String normalized = name.toLowerCase(Locale.ROOT);
      if (normalized.equals(classSimpleName) || isAccessorLike(name)) {
        continue;
      }
      names.add(name);
      if (names.size() >= 3) {
        break;
      }
    }
    return new ArrayList<>(names);
  }

  private List<String> collectPrincipalFieldNames(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getFields().isEmpty()) {
      return List.of();
    }
    final Set<String> names = new LinkedHashSet<>();
    for (final FieldInfo field : classInfo.getFields()) {
      if (field == null || field.getName() == null || field.getName().isBlank()) {
        continue;
      }
      names.add(field.getName().strip());
      if (names.size() >= 3) {
        break;
      }
    }
    return new ArrayList<>(names);
  }

  private boolean isAccessorLike(final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return false;
    }
    final String normalized = methodName.toLowerCase(Locale.ROOT);
    return normalized.startsWith("get")
        || normalized.startsWith("set")
        || normalized.startsWith("is")
        || normalized.startsWith("has")
        || "equals".equals(normalized)
        || "hashcode".equals(normalized)
        || "tostring".equals(normalized);
  }

  private boolean isConstructorLike(final String methodName) {
    if (methodName == null || methodName.isBlank()) {
      return false;
    }
    return Character.isUpperCase(methodName.strip().charAt(0));
  }

  private String localized(
      final boolean japanese, final String jaKey, final String enKey, final Object... args) {
    return MessageSource.getMessage(japanese ? jaKey : enKey, args);
  }
}
