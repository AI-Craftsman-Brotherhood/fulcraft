package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import java.util.List;
import java.util.Optional;

public class BranchSummaryResult {

  private final BranchSummary branchSummary;

  private final List<RepresentativePath> representativePaths;

  private final boolean usedFallback;

  private final String parseError;

  public BranchSummaryResult(
      final BranchSummary branchSummary,
      final List<RepresentativePath> representativePaths,
      final boolean usedFallback,
      final String parseError) {
    this.branchSummary = branchSummary;
    this.representativePaths =
        representativePaths == null ? List.of() : List.copyOf(representativePaths);
    this.usedFallback = usedFallback;
    this.parseError = parseError;
  }

  public Optional<BranchSummary> branchSummary() {
    return Optional.ofNullable(branchSummary);
  }

  public List<RepresentativePath> representativePaths() {
    return representativePaths;
  }

  public boolean usedFallback() {
    return usedFallback;
  }

  public Optional<String> parseError() {
    return Optional.ofNullable(parseError);
  }
}
