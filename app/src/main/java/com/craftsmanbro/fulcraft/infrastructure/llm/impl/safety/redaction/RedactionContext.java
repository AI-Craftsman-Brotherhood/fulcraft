package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

public final class RedactionContext {

  private static final ThreadLocal<RedactionReport> LAST_REPORT = new ThreadLocal<>();

  private static final ThreadLocal<String> LAST_PROMPT = new ThreadLocal<>();

  private RedactionContext() {}

  public static void setReport(final RedactionReport report) {
    setOrRemove(LAST_REPORT, report);
  }

  public static void setPrompt(final String prompt) {
    setOrRemove(LAST_PROMPT, prompt);
  }

  public static RedactionReport consumeReport() {
    return consumeAndRemove(LAST_REPORT);
  }

  public static String consumePrompt() {
    return consumeAndRemove(LAST_PROMPT);
  }

  private static <T> void setOrRemove(final ThreadLocal<T> holder, final T value) {
    if (value == null) {
      holder.remove();
      return;
    }
    holder.set(value);
  }

  private static <T> T consumeAndRemove(final ThreadLocal<T> holder) {
    final T value = holder.get();
    holder.remove();
    return value;
  }
}
