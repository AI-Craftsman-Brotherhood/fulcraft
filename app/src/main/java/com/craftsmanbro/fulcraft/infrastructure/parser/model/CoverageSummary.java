package com.craftsmanbro.fulcraft.infrastructure.parser.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Summary of code coverage metrics from test execution.
 *
 * <p>Captures line and branch coverage at various levels (package, class, method).
 */
public class CoverageSummary {

  @JsonProperty("line_covered")
  private int lineCovered;

  @JsonProperty("line_total")
  private int lineTotal;

  @JsonProperty("branch_covered")
  private int branchCovered;

  @JsonProperty("branch_total")
  private int branchTotal;

  @JsonProperty("instruction_covered")
  private int instructionCovered;

  @JsonProperty("instruction_total")
  private int instructionTotal;

  @JsonProperty("method_covered")
  private int methodCovered;

  @JsonProperty("method_total")
  private int methodTotal;

  @JsonProperty("class_covered")
  private int classCovered;

  @JsonProperty("class_total")
  private int classTotal;

  // === Getters ===
  public int getLineCovered() {
    return lineCovered;
  }

  public int getLineTotal() {
    return lineTotal;
  }

  public int getBranchCovered() {
    return branchCovered;
  }

  public int getBranchTotal() {
    return branchTotal;
  }

  public int getInstructionCovered() {
    return instructionCovered;
  }

  public int getInstructionTotal() {
    return instructionTotal;
  }

  public int getMethodCovered() {
    return methodCovered;
  }

  public int getMethodTotal() {
    return methodTotal;
  }

  public int getClassCovered() {
    return classCovered;
  }

  public int getClassTotal() {
    return classTotal;
  }

  // === Setters ===
  public void setLineCovered(final int lineCovered) {
    this.lineCovered = lineCovered;
  }

  public void setLineTotal(final int lineTotal) {
    this.lineTotal = lineTotal;
  }

  public void setBranchCovered(final int branchCovered) {
    this.branchCovered = branchCovered;
  }

  public void setBranchTotal(final int branchTotal) {
    this.branchTotal = branchTotal;
  }

  public void setInstructionCovered(final int instructionCovered) {
    this.instructionCovered = instructionCovered;
  }

  public void setInstructionTotal(final int instructionTotal) {
    this.instructionTotal = instructionTotal;
  }

  public void setMethodCovered(final int methodCovered) {
    this.methodCovered = methodCovered;
  }

  public void setMethodTotal(final int methodTotal) {
    this.methodTotal = methodTotal;
  }

  public void setClassCovered(final int classCovered) {
    this.classCovered = classCovered;
  }

  public void setClassTotal(final int classTotal) {
    this.classTotal = classTotal;
  }

  // === Computed Rates ===
  /** Line coverage rate (0.0 - 1.0) */
  @JsonProperty("line_coverage_rate")
  public double getLineCoverageRate() {
    return lineTotal == 0 ? 0.0 : (double) lineCovered / lineTotal;
  }

  /** Branch coverage rate (0.0 - 1.0) */
  @JsonProperty("branch_coverage_rate")
  public double getBranchCoverageRate() {
    return branchTotal == 0 ? 0.0 : (double) branchCovered / branchTotal;
  }

  /** Instruction coverage rate (0.0 - 1.0) */
  @JsonProperty("instruction_coverage_rate")
  public double getInstructionCoverageRate() {
    return instructionTotal == 0 ? 0.0 : (double) instructionCovered / instructionTotal;
  }

  /** Method coverage rate (0.0 - 1.0) */
  @JsonProperty("method_coverage_rate")
  public double getMethodCoverageRate() {
    return methodTotal == 0 ? 0.0 : (double) methodCovered / methodTotal;
  }

  /** Class coverage rate (0.0 - 1.0) */
  @JsonProperty("class_coverage_rate")
  public double getClassCoverageRate() {
    return classTotal == 0 ? 0.0 : (double) classCovered / classTotal;
  }

  // === Increment Methods ===
  public void addLineCovered(final int count) {
    this.lineCovered += count;
  }

  public void addLineTotal(final int count) {
    this.lineTotal += count;
  }

  public void addBranchCovered(final int count) {
    this.branchCovered += count;
  }

  public void addBranchTotal(final int count) {
    this.branchTotal += count;
  }

  public void addInstructionCovered(final int count) {
    this.instructionCovered += count;
  }

  public void addInstructionTotal(final int count) {
    this.instructionTotal += count;
  }

  public void addMethodCovered(final int count) {
    this.methodCovered += count;
  }

  public void addMethodTotal(final int count) {
    this.methodTotal += count;
  }

  public void addClassCovered(final int count) {
    this.classCovered += count;
  }

  public void addClassTotal(final int count) {
    this.classTotal += count;
  }

  /** Merge another coverage summary into this one. */
  public void merge(final CoverageSummary other) {
    if (other == null) {
      return;
    }
    this.lineCovered += other.lineCovered;
    this.lineTotal += other.lineTotal;
    this.branchCovered += other.branchCovered;
    this.branchTotal += other.branchTotal;
    this.instructionCovered += other.instructionCovered;
    this.instructionTotal += other.instructionTotal;
    this.methodCovered += other.methodCovered;
    this.methodTotal += other.methodTotal;
    this.classCovered += other.classCovered;
    this.classTotal += other.classTotal;
  }

  @Override
  public String toString() {
    return String.format(
        "CoverageSummary[line=%.1f%% (%d/%d), branch=%.1f%% (%d/%d)]",
        getLineCoverageRate() * 100,
        lineCovered,
        lineTotal,
        getBranchCoverageRate() * 100,
        branchCovered,
        branchTotal);
  }
}
