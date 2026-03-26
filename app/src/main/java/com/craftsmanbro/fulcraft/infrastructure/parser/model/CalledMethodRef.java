package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CalledMethodRef {

  @JsonProperty("raw")
  private String raw;

  @JsonProperty("resolved")
  private String resolved;

  @JsonProperty("status")
  private ResolutionStatus status;

  @JsonProperty("confidence")
  private double confidence;

  @JsonProperty("source")
  private String source;

  @JsonProperty("candidates")
  private List<String> candidates = new ArrayList<>();

  @JsonProperty("argument_literals")
  private List<String> argumentLiterals = new ArrayList<>();

  public CalledMethodRef() {}

  public CalledMethodRef(
      final String raw,
      final String resolved,
      final ResolutionStatus status,
      final double confidence,
      final String source,
      final List<String> candidates) {
    this(raw, resolved, status, confidence, source, candidates, null);
  }

  public CalledMethodRef(
      final String raw,
      final String resolved,
      final ResolutionStatus status,
      final double confidence,
      final String source,
      final List<String> candidates,
      final List<String> argumentLiterals) {
    this.raw = raw;
    this.resolved = resolved;
    this.status = status;
    this.confidence = confidence;
    this.source = source;
    this.candidates = Objects.requireNonNullElseGet(candidates, ArrayList::new);
    this.argumentLiterals = Objects.requireNonNullElseGet(argumentLiterals, ArrayList::new);
  }

  public String getRaw() {
    return raw;
  }

  public void setRaw(final String raw) {
    this.raw = raw;
  }

  public String getResolved() {
    return resolved;
  }

  public void setResolved(final String resolved) {
    this.resolved = resolved;
  }

  public ResolutionStatus getStatus() {
    return status;
  }

  public void setStatus(final ResolutionStatus status) {
    this.status = status;
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(final double confidence) {
    this.confidence = confidence;
  }

  public String getSource() {
    return source;
  }

  public void setSource(final String source) {
    this.source = source;
  }

  public List<String> getCandidates() {
    if (candidates == null) {
      candidates = new ArrayList<>();
    }
    return Collections.unmodifiableList(candidates);
  }

  public void setCandidates(final List<String> candidates) {
    this.candidates = Objects.requireNonNullElseGet(candidates, ArrayList::new);
  }

  public List<String> getArgumentLiterals() {
    if (argumentLiterals == null) {
      argumentLiterals = new ArrayList<>();
    }
    return Collections.unmodifiableList(argumentLiterals);
  }

  public void setArgumentLiterals(final List<String> argumentLiterals) {
    this.argumentLiterals = Objects.requireNonNullElseGet(argumentLiterals, ArrayList::new);
  }
}
