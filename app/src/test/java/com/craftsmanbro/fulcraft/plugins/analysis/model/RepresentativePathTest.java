package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class RepresentativePathTest {

  @Test
  void requiredConditions_defaultsToEmptyAndIsUnmodifiable() {
    RepresentativePath path = new RepresentativePath();

    path.setRequiredConditions(null);

    assertThat(path.getRequiredConditions()).isEmpty();
    assertThatThrownBy(() -> path.getRequiredConditions().add("cond"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void requiredConditions_usesProvidedList() {
    RepresentativePath path = new RepresentativePath();

    path.setRequiredConditions(List.of("a", "b"));

    assertThat(path.getRequiredConditions()).containsExactly("a", "b");
  }
}
