package com.craftsmanbro.fulcraft.plugins.document.core.llm.context;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds section-6 open-question lines from uncertain dynamic facts and validation reasons. */
public final class LlmOpenQuestionBuilder {

  private static final Pattern TRAILING_METHOD_NAME_PATTERN =
      Pattern.compile("[:：]\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*$");

  public List<String> buildInterfaceOpenQuestions(
      final Set<String> uncertainDynamicMethodDisplayNames, final boolean japanese) {
    final LinkedHashSet<String> questions = new LinkedHashSet<>();
    if (japanese) {
      questions.add(msg("document.llm.open_question.interface_overview.ja"));
    } else {
      questions.add(msg("document.llm.open_question.interface_overview.en"));
    }
    questions.addAll(
        buildFallbackOpenQuestions(
            List.of("interface"), uncertainDynamicMethodDisplayNames, List.of(), japanese));
    return new ArrayList<>(questions);
  }

  public List<String> buildFallbackOpenQuestions(
      final List<String> methodNames,
      final Set<String> uncertainDynamicMethodDisplayNames,
      final List<String> validationReasons,
      final boolean japanese) {
    final LinkedHashSet<String> questions = new LinkedHashSet<>();
    if (methodNames == null || methodNames.isEmpty()) {
      return List.of();
    }
    if (uncertainDynamicMethodDisplayNames != null) {
      for (final String uncertainMethod : uncertainDynamicMethodDisplayNames) {
        if (uncertainMethod == null || uncertainMethod.isBlank()) {
          continue;
        }
        if (japanese) {
          questions.add(msg("document.llm.open_question.dynamic_candidate.ja", uncertainMethod));
        } else {
          questions.add(msg("document.llm.open_question.dynamic_candidate.en", uncertainMethod));
        }
      }
    }
    if (validationReasons != null) {
      for (final String reason : validationReasons) {
        if (reason == null || reason.isBlank()) {
          continue;
        }
        if (reason.contains("外部メソッド存在を断定")
            || reason.contains("External method existence asserted")) {
          final String methodName = extractTrailingMethodName(reason);
          if (!methodName.isBlank()) {
            if (japanese) {
              questions.add(msg("document.llm.open_question.declaring_type_manual.ja", methodName));
            } else {
              questions.add(msg("document.llm.open_question.declaring_type_manual.en", methodName));
            }
          }
        }
      }
    }
    return new ArrayList<>(questions);
  }

  private String extractTrailingMethodName(final String reason) {
    if (reason == null || reason.isBlank()) {
      return "";
    }
    final Matcher matcher = TRAILING_METHOD_NAME_PATTERN.matcher(reason.strip());
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1);
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
