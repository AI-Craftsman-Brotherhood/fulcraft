package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import java.util.ArrayList;
import java.util.List;

/** Builds fallback detailed-design documents from precomputed method section data. */
public final class LlmFallbackDocumentBuilder {

  public String build(final FallbackDocumentInput input) {
    if (input == null) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    final boolean japanese = input.japanese();
    sb.append("# ")
        .append(
            localized(
                japanese,
                "document.llm.doc.title.ja",
                "document.llm.doc.title.en",
                input.className()))
        .append("\n\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.purpose.ja",
                "document.llm.doc.section.purpose.en"))
        .append("\n");
    appendPurposeLines(
        sb,
        input.purposeLines(),
        localized(
            japanese,
            "document.llm.fallback.doc.purpose_line.ja",
            "document.llm.fallback.doc.purpose_line.en",
            input.className()));
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.external.ja",
                "document.llm.doc.section.external.en"))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.class_name.ja",
                "document.llm.doc.class_spec.class_name.en",
                input.className()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.package.ja",
                "document.llm.doc.class_spec.package.en",
                input.packageName()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.file_path.ja",
                "document.llm.doc.class_spec.file_path.en",
                input.filePath()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.lines.ja",
                "document.llm.doc.class_spec.lines.en",
                input.loc()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.method_count.ja",
                "document.llm.doc.class_spec.method_count.en",
                input.methodCount()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.class_type.ja",
                "document.llm.doc.class_spec.class_type.en",
                input.classType()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.extends.ja",
                "document.llm.doc.class_spec.extends.en",
                input.extendsInfo()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.implements.ja",
                "document.llm.doc.class_spec.implements.en",
                input.implementsInfo()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.class_attributes.ja",
                "document.llm.doc.class_spec.class_attributes.en"))
        .append("\n");
    for (final String line : input.classAttributes().split("\\R")) {
      sb.append("  ").append(line).append("\n");
    }
    sb.append(
            localized(
                japanese,
                "document.llm.doc.class_spec.fields.ja",
                "document.llm.doc.class_spec.fields.en"))
        .append("\n");
    appendIndentedLines(
        sb,
        input.fieldsInfo(),
        localized(japanese, "document.llm.doc.none.ja", "document.llm.doc.none.en"));
    sb.append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.methods.ja",
                "document.llm.doc.section.methods.en"))
        .append("\n\n");
    final List<FallbackMethodSection> sections = input.methodSections();
    for (int i = 0; i < sections.size(); i++) {
      final int sectionNo = i + 1;
      final FallbackMethodSection section = sections.get(i);
      sb.append(msg("document.llm.doc.method.header", sectionNo, section.methodName()))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.inputs.ja",
                  "document.llm.doc.method.sub.inputs.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.fallback.doc.method.signature_line.ja",
                  "document.llm.fallback.doc.method.signature_line.en",
                  section.signature()))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.preconditions.ja",
                  "document.llm.doc.method.sub.preconditions.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          section.preconditions(),
          localized(japanese, "document.llm.doc.none.ja", "document.llm.doc.none.en"));
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.postconditions.ja",
                  "document.llm.doc.method.sub.postconditions.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          section.postconditions(),
          localized(
              japanese,
              "document.llm.doc.no_analysis_data.ja",
              "document.llm.doc.no_analysis_data.en"));
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.normal_flow.ja",
                  "document.llm.doc.method.sub.normal_flow.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          section.normalFlows(),
          localized(
              japanese,
              "document.llm.doc.no_analysis_data.ja",
              "document.llm.doc.no_analysis_data.en"));
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.error_boundary.ja",
                  "document.llm.doc.method.sub.error_boundary.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          section.errorBoundaries(),
          localized(
              japanese,
              "document.llm.doc.no_analysis_data.ja",
              "document.llm.doc.no_analysis_data.en"));
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.dependencies.ja",
                  "document.llm.doc.method.sub.dependencies.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          wrapInBackticks(section.dependencyCalls()),
          localized(japanese, "document.llm.doc.none.ja", "document.llm.doc.none.en"));
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.test_viewpoints.ja",
                  "document.llm.doc.method.sub.test_viewpoints.en",
                  sectionNo))
          .append("\n");
      appendFallbackBulletList(
          sb,
          section.testViewpoints(),
          localized(
              japanese,
              "document.llm.fallback.doc.method.test_default.ja",
              "document.llm.fallback.doc.method.test_default.en"));
    }
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.cautions.ja",
                "document.llm.doc.section.cautions.en"))
        .append("\n");
    sb.append(input.cautionsInfo()).append("\n\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.recommendations.ja",
                "document.llm.doc.section.recommendations.en"))
        .append("\n");
    sb.append("- ")
        .append(localized(japanese, "document.llm.doc.none.ja", "document.llm.doc.none.en"))
        .append("\n\n");
    sb.append(
            localized(
                japanese,
                "document.llm.doc.section.open_questions.ja",
                "document.llm.doc.section.open_questions.en"))
        .append("\n");
    if (input.openQuestions().isEmpty()) {
      sb.append("- ")
          .append(localized(japanese, "document.llm.doc.none.ja", "document.llm.doc.none.en"))
          .append("\n");
    } else {
      for (final String openQuestion : input.openQuestions()) {
        sb.append("- ").append(openQuestion).append("\n");
      }
    }
    return sb.toString();
  }

  private void appendFallbackBulletList(
      final StringBuilder sb, final List<String> items, final String noneValue) {
    if (items == null || items.isEmpty()) {
      sb.append("- ").append(noneValue).append("\n\n");
      return;
    }
    int emitted = 0;
    for (final String item : items) {
      if (item == null || item.isBlank()) {
        continue;
      }
      sb.append("- ").append(item.strip()).append("\n");
      emitted++;
    }
    if (emitted == 0) {
      sb.append("- ").append(noneValue).append("\n");
    }
    sb.append("\n");
  }

  private void appendPurposeLines(
      final StringBuilder sb, final List<String> purposeLines, final String fallbackLine) {
    if (purposeLines == null || purposeLines.isEmpty()) {
      sb.append(fallbackLine).append("\n");
      return;
    }
    int emitted = 0;
    for (final String purposeLine : purposeLines) {
      if (purposeLine == null || purposeLine.isBlank()) {
        continue;
      }
      final String normalized = purposeLine.strip();
      if (normalized.startsWith("-")) {
        sb.append(normalized).append("\n");
      } else {
        sb.append("- ").append(normalized).append("\n");
      }
      emitted++;
    }
    if (emitted == 0) {
      sb.append(fallbackLine).append("\n");
    }
  }

  private List<String> wrapInBackticks(final List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    final List<String> wrapped = new ArrayList<>();
    for (final String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      final String token = value.strip();
      if (token.startsWith("`") && token.endsWith("`")) {
        wrapped.add(token);
      } else {
        wrapped.add("`" + token + "`");
      }
    }
    return wrapped;
  }

  private void appendIndentedLines(
      final StringBuilder sb, final String value, final String fallbackLine) {
    if (value == null || value.isBlank()) {
      sb.append("  - ").append(fallbackLine).append("\n");
      return;
    }
    int emitted = 0;
    for (final String line : value.split("\\R")) {
      if (line == null || line.isBlank()) {
        continue;
      }
      final String normalized = line.strip();
      if (normalized.startsWith("-")) {
        sb.append("  ").append(normalized).append("\n");
      } else {
        sb.append("  - ").append(normalized).append("\n");
      }
      emitted++;
    }
    if (emitted == 0) {
      sb.append("  - ").append(fallbackLine).append("\n");
    }
  }

  private String localized(
      final boolean japanese, final String jaKey, final String enKey, final Object... args) {
    return msg(japanese ? jaKey : enKey, args);
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  public record FallbackDocumentInput(
      boolean japanese,
      String className,
      List<String> purposeLines,
      String packageName,
      String filePath,
      int loc,
      int methodCount,
      String classType,
      String extendsInfo,
      String implementsInfo,
      String classAttributes,
      String fieldsInfo,
      List<FallbackMethodSection> methodSections,
      String cautionsInfo,
      List<String> openQuestions) {

    public FallbackDocumentInput {
      className = LlmDocumentTextUtils.emptyIfNull(className);
      purposeLines = purposeLines == null ? List.of() : List.copyOf(purposeLines);
      packageName = LlmDocumentTextUtils.emptyIfNull(packageName);
      filePath = LlmDocumentTextUtils.emptyIfNull(filePath);
      classType = LlmDocumentTextUtils.emptyIfNull(classType);
      extendsInfo = LlmDocumentTextUtils.emptyIfNull(extendsInfo);
      implementsInfo = LlmDocumentTextUtils.emptyIfNull(implementsInfo);
      classAttributes = classAttributes == null ? "" : classAttributes;
      fieldsInfo = fieldsInfo == null ? "" : fieldsInfo;
      methodSections = methodSections == null ? List.of() : List.copyOf(methodSections);
      cautionsInfo = cautionsInfo == null ? "" : cautionsInfo;
      openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
    }
  }

  public record FallbackMethodSection(
      String methodName,
      String signature,
      List<String> preconditions,
      List<String> postconditions,
      List<String> normalFlows,
      List<String> errorBoundaries,
      List<String> dependencyCalls,
      List<String> testViewpoints) {

    public FallbackMethodSection {
      methodName = LlmDocumentTextUtils.emptyIfNull(methodName);
      signature = LlmDocumentTextUtils.emptyIfNull(signature);
      preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
      postconditions = postconditions == null ? List.of() : List.copyOf(postconditions);
      normalFlows = normalFlows == null ? List.of() : List.copyOf(normalFlows);
      errorBoundaries = errorBoundaries == null ? List.of() : List.copyOf(errorBoundaries);
      dependencyCalls = dependencyCalls == null ? List.of() : List.copyOf(dependencyCalls);
      testViewpoints = testViewpoints == null ? List.of() : List.copyOf(testViewpoints);
    }
  }
}
