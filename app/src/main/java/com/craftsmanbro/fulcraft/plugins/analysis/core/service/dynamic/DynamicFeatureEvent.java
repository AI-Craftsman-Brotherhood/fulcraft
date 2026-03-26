package com.craftsmanbro.fulcraft.plugins.analysis.core.service.dynamic;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Represents a detected dynamic feature event. Immutable record for thread-safe collection during
 * analysis.
 */
public record DynamicFeatureEvent(
    @JsonProperty("feature_type") DynamicFeatureType featureType,
    @JsonProperty("feature_subtype") String featureSubtype,
    @JsonProperty("file_path") String filePath,
    @JsonProperty("class_fqn") String classFqn,
    @JsonProperty("method_sig") String methodSig,
    @JsonProperty("line_start") int lineStart,
    @JsonProperty("line_end") int lineEnd,
    @JsonProperty("snippet") String snippet,
    @JsonProperty("evidence") Map<String, String> evidence,
    @JsonProperty("severity") DynamicFeatureSeverity severity) {

  /** Compact constructor that normalizes fields and creates defensive copies. */
  public DynamicFeatureEvent {
    evidence = evidence != null ? Map.copyOf(evidence) : Map.of();
    snippet = truncateSnippet(snippet);
  }

  /** Maximum snippet length to avoid huge outputs */
  private static final int MAX_SNIPPET_LENGTH = 200;

  /** Creates an event with automatic snippet truncation. */
  public static DynamicFeatureEvent create(
      final DynamicFeatureType featureType,
      final String featureSubtype,
      final String filePath,
      final String classFqn,
      final String methodSig,
      final int lineStart,
      final int lineEnd,
      final String snippet,
      final Map<String, String> evidence,
      final DynamicFeatureSeverity severity) {
    return new DynamicFeatureEvent(
        featureType,
        featureSubtype,
        filePath,
        classFqn,
        methodSig,
        lineStart,
        lineEnd,
        snippet,
        evidence,
        severity);
  }

  private static String truncateSnippet(final String snippet) {
    if (snippet == null) {
      return null;
    }
    // Escape newlines and truncate
    final String escaped = snippet.replace("\n", "\\n").replace("\r", "");
    if (escaped.length() > MAX_SNIPPET_LENGTH) {
      return escaped.substring(0, MAX_SNIPPET_LENGTH - 3) + "...";
    }
    return escaped;
  }

  /** Builder for fluent event construction. */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private DynamicFeatureType featureType;

    private String featureSubtype;

    private String filePath;

    private String classFqn;

    private String methodSig;

    private int lineStart = -1;

    private int lineEnd = -1;

    private String snippet;

    private Map<String, String> evidence = Map.of();

    private DynamicFeatureSeverity severity = DynamicFeatureSeverity.MEDIUM;

    public Builder featureType(final DynamicFeatureType type) {
      this.featureType = type;
      return this;
    }

    public Builder featureSubtype(final String subtype) {
      this.featureSubtype = subtype;
      return this;
    }

    public Builder filePath(final String path) {
      this.filePath = path;
      return this;
    }

    public Builder classFqn(final String fqn) {
      this.classFqn = fqn;
      return this;
    }

    public Builder methodSig(final String sig) {
      this.methodSig = sig;
      return this;
    }

    public Builder lineStart(final int line) {
      this.lineStart = line;
      return this;
    }

    public Builder lineEnd(final int line) {
      this.lineEnd = line;
      return this;
    }

    public Builder snippet(final String s) {
      this.snippet = s;
      return this;
    }

    public Builder evidence(final Map<String, String> e) {
      this.evidence = e;
      return this;
    }

    public Builder severity(final DynamicFeatureSeverity s) {
      this.severity = s;
      return this;
    }

    public DynamicFeatureEvent build() {
      return DynamicFeatureEvent.create(
          featureType,
          featureSubtype,
          filePath,
          classFqn,
          methodSig,
          lineStart,
          lineEnd,
          snippet,
          evidence,
          severity);
    }
  }
}
