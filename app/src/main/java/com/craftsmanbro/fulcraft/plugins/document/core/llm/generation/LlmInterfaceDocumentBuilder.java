package com.craftsmanbro.fulcraft.plugins.document.core.llm.generation;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils;
import java.util.List;

/** Builds interface-class documents from declaration-only facts. */
public final class LlmInterfaceDocumentBuilder {

  public String build(final InterfaceDocumentInput input) {
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
    sb.append(
            localized(
                japanese,
                "document.llm.interface.doc.purpose_line1.ja",
                "document.llm.interface.doc.purpose_line1.en",
                input.className()))
        .append("\n");
    sb.append(
            localized(
                japanese,
                "document.llm.interface.doc.purpose_line2.ja",
                "document.llm.interface.doc.purpose_line2.en"))
        .append("\n");
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
    final List<InterfaceMethodSection> methodSections = input.methodSections();
    for (int i = 0; i < methodSections.size(); i++) {
      final int sectionNo = i + 1;
      final InterfaceMethodSection section = methodSections.get(i);
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
                  "document.llm.interface.doc.method.signature_line.ja",
                  "document.llm.interface.doc.method.signature_line.en",
                  section.signature()))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.preconditions.ja",
                  "document.llm.doc.method.sub.preconditions.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.interface.doc.method.preconditions_line.ja",
                  "document.llm.interface.doc.method.preconditions_line.en"))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.postconditions.ja",
                  "document.llm.doc.method.sub.postconditions.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.interface.doc.method.postconditions_line.ja",
                  "document.llm.interface.doc.method.postconditions_line.en"))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.normal_flow.ja",
                  "document.llm.doc.method.sub.normal_flow.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.interface.doc.method.normal_flow_line.ja",
                  "document.llm.interface.doc.method.normal_flow_line.en"))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.error_boundary.ja",
                  "document.llm.doc.method.sub.error_boundary.en",
                  sectionNo))
          .append("\n");
      sb.append(
          localized(
              japanese,
              "document.llm.interface.doc.method.exception_prefix.ja",
              "document.llm.interface.doc.method.exception_prefix.en"));
      if (section.thrownExceptions().isEmpty()) {
        sb.append(
            localized(
                japanese,
                "document.llm.interface.doc.method.exception_none.ja",
                "document.llm.interface.doc.method.exception_none.en"));
      } else {
        sb.append("`").append(String.join("`, `", section.thrownExceptions())).append("`");
      }
      sb.append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.dependencies.ja",
                  "document.llm.doc.method.sub.dependencies.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.interface.doc.method.dependencies_line.ja",
                  "document.llm.interface.doc.method.dependencies_line.en"))
          .append("\n\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.doc.method.sub.test_viewpoints.ja",
                  "document.llm.doc.method.sub.test_viewpoints.en",
                  sectionNo))
          .append("\n");
      sb.append(
              localized(
                  japanese,
                  "document.llm.interface.doc.method.test_viewpoints_line.ja",
                  "document.llm.interface.doc.method.test_viewpoints_line.en"))
          .append("\n\n");
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
    for (final String openQuestion : input.openQuestions()) {
      sb.append("- ").append(openQuestion).append("\n");
    }
    return sb.toString();
  }

  public record InterfaceDocumentInput(
      boolean japanese,
      String className,
      String packageName,
      String filePath,
      int loc,
      int methodCount,
      String classType,
      String extendsInfo,
      String implementsInfo,
      String classAttributes,
      String fieldsInfo,
      List<InterfaceMethodSection> methodSections,
      String cautionsInfo,
      List<String> openQuestions) {

    public InterfaceDocumentInput {
      className = LlmDocumentTextUtils.emptyIfNull(className);
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

  public record InterfaceMethodSection(
      String methodName, String signature, List<String> thrownExceptions) {

    public InterfaceMethodSection {
      methodName = LlmDocumentTextUtils.emptyIfNull(methodName);
      signature = LlmDocumentTextUtils.emptyIfNull(signature);
      thrownExceptions = thrownExceptions == null ? List.of() : List.copyOf(thrownExceptions);
    }
  }

  private String localized(
      final boolean japanese, final String jaKey, final String enKey, final Object... args) {
    return msg(japanese ? jaKey : enKey, args);
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
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
}
