package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents a resolved dynamic feature (Reflection, ServiceLoader, etc.). Only "確実ケース" (certain
 * cases) are resolved.
 */
public record DynamicResolution(
    @JsonProperty("type") String type,
    @JsonProperty("subtype") String subtype,
    @JsonProperty("file_path") String filePath,
    @JsonProperty("class_fqn") String classFqn,
    @JsonProperty("method_sig") String methodSig,
    @JsonProperty("line_start") int lineStart,
    @JsonProperty("resolved_class_fqn") String resolvedClassFqn,
    @JsonProperty("resolved_method_sig") String resolvedMethodSig,
    @JsonProperty("providers") List<String> providers,
    @JsonProperty("candidates") List<String> candidates,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("trust_level") TrustLevel trustLevel,
    @JsonProperty("reason_code") DynamicReasonCode reasonCode,
    @JsonProperty("rule_id") ResolutionRuleId ruleId,
    @JsonProperty("evidence") Map<String, String> evidence) {

  public static final String TYPE_RESOLUTION = "RESOLUTION";

  /** Compact constructor that creates defensive copies of collections. */
  public DynamicResolution {
    if (type == null || type.isBlank()) {
      type = TYPE_RESOLUTION;
    }
    if (trustLevel == null) {
      trustLevel = TrustLevel.fromConfidence(confidence);
    }
    providers = providers != null ? List.copyOf(providers) : List.of();
    candidates = candidates != null ? List.copyOf(candidates) : List.of();
    evidence = evidence != null ? Map.copyOf(evidence) : Map.of();
  }

  // Resolution subtypes
  public static final String CLASS_FORNAME_LITERAL = "CLASS_FORNAME_LITERAL";

  public static final String METHOD_RESOLVE = "METHOD_RESOLVE";

  public static final String METHOD_INVOKE_LINK = "METHOD_INVOKE_LINK";

  public static final String FIELD_RESOLVE = "FIELD_RESOLVE";

  public static final String SERVICELOADER_PROVIDERS = "SERVICELOADER_PROVIDERS";

  public static final String BRANCH_CANDIDATES = "BRANCH_CANDIDATES";

  public static final String INTERPROCEDURAL_SINGLE = "INTERPROCEDURAL_SINGLE";

  public static final String EXPERIMENTAL_CANDIDATES = "EXPERIMENTAL_CANDIDATES";

  /** Builder for fluent construction. */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String type = TYPE_RESOLUTION;

    private String subtype;

    private String filePath;

    private String classFqn;

    private String methodSig;

    private int lineStart = -1;

    private String resolvedClassFqn;

    private String resolvedMethodSig;

    private List<String> providers;

    private List<String> candidates;

    private double confidence = 1.0;

    private TrustLevel trustLevel = TrustLevel.fromConfidence(confidence);

    private boolean trustLevelExplicitlySet;

    private DynamicReasonCode reasonCode;

    private ResolutionRuleId ruleId;

    private Map<String, String> evidence = Map.of();

    public Builder type(final String t) {
      this.type = t;
      return this;
    }

    public Builder subtype(final String s) {
      this.subtype = s;
      return this;
    }

    public Builder filePath(final String p) {
      this.filePath = p;
      return this;
    }

    public Builder classFqn(final String c) {
      this.classFqn = c;
      return this;
    }

    public Builder methodSig(final String m) {
      this.methodSig = m;
      return this;
    }

    public Builder lineStart(final int l) {
      this.lineStart = l;
      return this;
    }

    public Builder resolvedClassFqn(final String r) {
      this.resolvedClassFqn = r;
      return this;
    }

    public Builder resolvedMethodSig(final String r) {
      this.resolvedMethodSig = r;
      return this;
    }

    public Builder providers(final List<String> p) {
      this.providers = p;
      return this;
    }

    public Builder candidates(final List<String> c) {
      this.candidates = c;
      return this;
    }

    public Builder confidence(final double c) {
      this.confidence = c;
      if (!trustLevelExplicitlySet) {
        this.trustLevel = TrustLevel.fromConfidence(c);
      }
      return this;
    }

    public Builder trustLevel(final TrustLevel trustLevel) {
      this.trustLevel = trustLevel;
      this.trustLevelExplicitlySet = true;
      return this;
    }

    public Builder evidence(final Map<String, String> e) {
      this.evidence = e;
      return this;
    }

    public Builder reasonCode(final DynamicReasonCode r) {
      this.reasonCode = r;
      return this;
    }

    public Builder ruleId(final ResolutionRuleId r) {
      this.ruleId = r;
      return this;
    }

    public DynamicResolution build() {
      return new DynamicResolution(
          type,
          subtype,
          filePath,
          classFqn,
          methodSig,
          lineStart,
          resolvedClassFqn,
          resolvedMethodSig,
          providers,
          candidates,
          confidence,
          trustLevel != null ? trustLevel : TrustLevel.fromConfidence(confidence),
          reasonCode,
          ruleId,
          evidence);
    }
  }
}
