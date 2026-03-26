package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveDataRedactor {

  public static final String DEFAULT_MASK = "[REDACTED]";

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern PEM_KEY_PATTERN =
      Pattern.compile(
          "-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----[\\s\\S]*?-----END (?:[A-Z ]+ )?PRIVATE KEY-----",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern AUTH_BEARER_PATTERN =
      Pattern.compile("(?i)(\\bAuthorization\\s*:\\s*Bearer\\s+)([A-Za-z0-9._\\-+/=]+)");

  private static final String KEY_NAME_REGEX =
      "\\b[-\\w]*(?:api[-_]?key|access[-_]?token|token|secret|password)[-\\w]*\\b";

  private static final Pattern KEY_VALUE_QUOTED_PATTERN =
      Pattern.compile(
          "(" + KEY_NAME_REGEX + ")(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*')", Pattern.CASE_INSENSITIVE);

  private static final Pattern KEY_VALUE_BARE_PATTERN =
      Pattern.compile(
          "(" + KEY_NAME_REGEX + ")(\\s*[:=]\\s*)([^\\s'\";]+)", Pattern.CASE_INSENSITIVE);

  private static final Pattern JWT_PATTERN =
      Pattern.compile(
          "(?<![A-Za-z0-9_\\-])([A-Za-z0-9_\\-]{10,})\\.([A-Za-z0-9_\\-]{10,})\\.([A-Za-z0-9_\\-]{8,})(?![A-Za-z0-9_\\-])");

  private static final Pattern CREDIT_CARD_CANDIDATE_PATTERN =
      Pattern.compile("(?<!\\d)(?:\\d[ -]*?){13,19}(?!\\d)");

  private final String mask;

  public SensitiveDataRedactor() {
    this(DEFAULT_MASK);
  }

  public SensitiveDataRedactor(final String mask) {
    this.mask =
        Objects.requireNonNull(
            mask,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "mask must not be null"));
  }

  public RedactionResult redact(final String input) {
    Objects.requireNonNull(
        input,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "input must not be null"));
    String text = input;
    final RedactionStep pemStep = redactSimple(text, PEM_KEY_PATTERN);
    text = pemStep.text();
    final int pemKeyCount = pemStep.count();
    final RedactionStep bearerStep =
        redactWithPrefix(text, AUTH_BEARER_PATTERN, matcher -> matcher.group(1) + mask);
    text = bearerStep.text();
    final RedactionStep keyValueQuotedStep =
        redactWithPrefix(text, KEY_VALUE_QUOTED_PATTERN, this::maskQuotedKeyValue);
    text = keyValueQuotedStep.text();
    final RedactionStep keyValueBareStep =
        redactWithPrefix(text, KEY_VALUE_BARE_PATTERN, this::maskBareKeyValue);
    text = keyValueBareStep.text();
    final int authTokenCount =
        bearerStep.count() + keyValueQuotedStep.count() + keyValueBareStep.count();
    final RedactionStep jwtStep = redactSimple(text, JWT_PATTERN);
    text = jwtStep.text();
    final int jwtCount = jwtStep.count();
    final RedactionStep emailStep = redactSimple(text, EMAIL_PATTERN);
    text = emailStep.text();
    final int emailCount = emailStep.count();
    final RedactionStep cardStep = redactCreditCards(text);
    text = cardStep.text();
    final int creditCardCount = cardStep.count();
    final RedactionReport report =
        new RedactionReport(emailCount, creditCardCount, pemKeyCount, authTokenCount, jwtCount);
    return new RedactionResult(text, report);
  }

  private RedactionStep redactSimple(final String input, final Pattern pattern) {
    final Matcher matcher = pattern.matcher(input);
    if (!matcher.find()) {
      return new RedactionStep(input, 0);
    }
    final StringBuilder sb = new StringBuilder();
    int count = 0;
    do {
      count++;
      matcher.appendReplacement(sb, Matcher.quoteReplacement(mask));
    } while (matcher.find());
    matcher.appendTail(sb);
    return new RedactionStep(sb.toString(), count);
  }

  private RedactionStep redactWithPrefix(
      final String input,
      final Pattern pattern,
      final java.util.function.Function<Matcher, String> replacement) {
    final Matcher matcher = pattern.matcher(input);
    if (!matcher.find()) {
      return new RedactionStep(input, 0);
    }
    final StringBuilder sb = new StringBuilder();
    int count = 0;
    do {
      count++;
      matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.apply(matcher)));
    } while (matcher.find());
    matcher.appendTail(sb);
    return new RedactionStep(sb.toString(), count);
  }

  private String maskQuotedKeyValue(final Matcher matcher) {
    final String value = matcher.group(3);
    final char quote = value.charAt(0);
    return matcher.group(1) + matcher.group(2) + quote + mask + quote;
  }

  private String maskBareKeyValue(final Matcher matcher) {
    return matcher.group(1) + matcher.group(2) + mask;
  }

  private RedactionStep redactCreditCards(final String input) {
    final Matcher matcher = CREDIT_CARD_CANDIDATE_PATTERN.matcher(input);
    if (!matcher.find()) {
      return new RedactionStep(input, 0);
    }
    final StringBuilder sb = new StringBuilder();
    int count = 0;
    do {
      final String candidate = matcher.group();
      final String digits = candidate.replaceAll("[^0-9]", "");
      if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
        count++;
        matcher.appendReplacement(sb, Matcher.quoteReplacement(mask));
      } else {
        matcher.appendReplacement(sb, Matcher.quoteReplacement(candidate));
      }
    } while (matcher.find());
    matcher.appendTail(sb);
    return new RedactionStep(sb.toString(), count);
  }

  private boolean passesLuhn(final String digits) {
    int sum = 0;
    boolean doubleDigit = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int digit = digits.charAt(i) - '0';
      if (doubleDigit) {
        digit *= 2;
        if (digit > 9) {
          digit -= 9;
        }
      }
      sum += digit;
      doubleDigit = !doubleDigit;
    }
    return sum % 10 == 0;
  }

  private record RedactionStep(String text, int count) {}
}
