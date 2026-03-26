package com.craftsmanbro.fulcraft.plugins.analysis.core.service.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.plugins.analysis.model.BranchSummary;
import com.craftsmanbro.fulcraft.plugins.analysis.model.RepresentativePath;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchSummaryResultTest {

  @Test
  void constructor_handlesNulls() {
    BranchSummaryResult result = new BranchSummaryResult(null, null, false, null);

    assertThat(result.branchSummary()).isEmpty();
    assertThat(result.representativePaths()).isEmpty();
    assertThat(result.usedFallback()).isFalse();
    assertThat(result.parseError()).isEmpty();
  }

  @Test
  void constructor_copiesRepresentativePathsAndKeepsValues() {
    BranchSummary summary = new BranchSummary();
    List<RepresentativePath> paths = new ArrayList<>();
    RepresentativePath path = new RepresentativePath();
    path.setId("path-1");
    paths.add(path);

    BranchSummaryResult result = new BranchSummaryResult(summary, paths, true, "parse-error");
    paths.add(new RepresentativePath());

    assertThat(result.branchSummary()).contains(summary);
    assertThat(result.representativePaths()).hasSize(1);
    assertThat(result.representativePaths().getFirst().getId()).isEqualTo("path-1");
    assertThat(result.usedFallback()).isTrue();
    assertThat(result.parseError()).contains("parse-error");
    assertThatThrownBy(() -> result.representativePaths().add(new RepresentativePath()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
