package com.craftsmanbro.fulcraft.infrastructure.security.impl;

import com.craftsmanbro.fulcraft.infrastructure.security.contract.SecretMaskingPort;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility for masking secrets in log and UI output. */
public final class SecretMasker implements SecretMaskingPort {

  private static final String MASK = "****";

  private static final SecretMasker INSTANCE = new SecretMasker();

  private static final Pattern PEM_PRIVATE_KEY =
      Pattern.compile(
          "-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]+?-----END [A-Z ]*PRIVATE KEY-----");

  private static final Pattern AUTH_WITH_SCHEME =
      Pattern.compile(
          "(?i)(\"?\\bAuthorization\\b\"?\\s*[:=]\\s*\"?\\s*[^\\s\"]+\\s+)([^\\r\\n\"]+)");

  private static final Pattern KEY_VALUE =
      Pattern.compile(
          "(?i)((?:\"?(?:\\b(?:api_key|apikey|token|access_token|refresh_token|client_secret|secret|password|passwd)\\b|x-api-key|x-auth-token)\"?)\\s*[:=]\\s*)(\")?([^\"\\s,;]+)(\")?");

  private static final Pattern JWT_LIKE =
      Pattern.compile("\\b[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\b");

  private static final Pattern LONG_RANDOM_TOKEN =
      Pattern.compile("\\b(?=[A-Za-z0-9_-]{32,}\\b)(?=.*[A-Za-z])(?=.*\\d)[A-Za-z0-9_-]{32,}\\b");

  private static final Pattern HEX_TOKEN = Pattern.compile("\\b[a-fA-F0-9]{32,}\\b");

  private static final Pattern BASE64_TOKEN = Pattern.compile("\\b[A-Za-z0-9+/]{32,}={0,2}\\b");

  private static final Pattern[] TOKEN_LIKE_PATTERNS = {
    JWT_LIKE, LONG_RANDOM_TOKEN, HEX_TOKEN, BASE64_TOKEN
  };

  private SecretMasker() {}

  public static String mask(final String input) {
    return INSTANCE.maskText(input);
  }

  public static String maskStackTrace(final Throwable throwable) {
    return INSTANCE.maskThrowable(throwable);
  }

  public static SecretMaskingPort port() {
    return INSTANCE;
  }

  @Override
  public String maskText(final String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return maskStructuredSecrets(input);
  }

  @Override
  public String maskThrowable(final Throwable throwable) {
    if (throwable == null) {
      return "";
    }
    return maskText(stackTraceToString(throwable));
  }

  private static String stackTraceToString(final Throwable throwable) {
    final StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  private static String maskStructuredSecrets(final String input) {
    String masked = replaceAll(input, PEM_PRIVATE_KEY, MASK);
    masked = maskWithPrefix(masked, AUTH_WITH_SCHEME);
    masked = maskWithPrefixAndQuotes(masked, KEY_VALUE);
    return maskTokenLikeValues(masked);
  }

  private static String maskTokenLikeValues(final String input) {
    return applyFixedMasks(input, TOKEN_LIKE_PATTERNS);
  }

  private static String applyFixedMasks(final String input, final Pattern... patterns) {
    String masked = input;
    for (final Pattern pattern : patterns) {
      masked = replaceAll(masked, pattern, MASK);
    }
    return masked;
  }

  private static String replaceAll(
      final String input, final Pattern pattern, final String replacement) {
    return pattern.matcher(input).replaceAll(replacement);
  }

  private static String replaceMatches(
      final String input, final Pattern pattern, final MatchReplacementBuilder replacementBuilder) {
    final Matcher matcher = pattern.matcher(input);
    final StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement(replacementBuilder.buildReplacement(matcher)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  private static String maskWithPrefix(final String input, final Pattern pattern) {
    return replaceMatches(input, pattern, matcher -> matcher.group(1) + MASK);
  }

  private static String maskWithPrefixAndQuotes(final String input, final Pattern pattern) {
    return replaceMatches(
        input,
        pattern,
        matcher -> {
          final String prefix = matcher.group(1);
          final String openQuote = emptyIfNull(matcher.group(2));
          final String closeQuote = emptyIfNull(matcher.group(4));
          return prefix + openQuote + MASK + closeQuote;
        });
  }

  private static String emptyIfNull(final String value) {
    return value == null ? "" : value;
  }

  @FunctionalInterface
  private interface MatchReplacementBuilder {
    String buildReplacement(Matcher matcher);
  }
}
