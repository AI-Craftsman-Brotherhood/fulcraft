package com.craftsmanbro.fulcraft.plugins.document.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromptInputCanonicalizerTest {

  @Test
  void sortStrings_shouldSortAndDropNulls() {
    List<String> input = Arrays.asList("banana", null, "Apple", "cherry");

    List<String> sorted = PromptInputCanonicalizer.sortStrings(input);

    assertThat(sorted).containsExactly("Apple", "banana", "cherry");
  }

  @Test
  void sortStrings_collection_shouldSortDeterministically() {
    Set<String> input = Set.of("banana", "Apple", "cherry", "Date");

    List<String> sorted = PromptInputCanonicalizer.sortStrings(input);

    assertThat(sorted).containsExactly("Apple", "Date", "banana", "cherry");
  }

  @Test
  void sortStrings_shouldPreserveDuplicatesWithCaseSensitiveOrdering() {
    List<String> input = Arrays.asList("beta", "Alpha", "alpha", "beta");

    List<String> sorted = PromptInputCanonicalizer.sortStrings(input);

    assertThat(sorted).containsExactly("Alpha", "alpha", "beta", "beta");
  }

  @Test
  void sortMethods_shouldSortBySignatureThenName() {
    MethodInfo methodWithNullSignature = new MethodInfo();
    methodWithNullSignature.setSignature(null);
    methodWithNullSignature.setName("nullSig");

    MethodInfo methodA = new MethodInfo();
    methodA.setSignature("a()");
    methodA.setName("zeta");

    MethodInfo methodB = new MethodInfo();
    methodB.setSignature("a()");
    methodB.setName("alpha");

    MethodInfo methodC = new MethodInfo();
    methodC.setSignature("b()");
    methodC.setName("beta");

    List<MethodInfo> sorted =
        PromptInputCanonicalizer.sortMethods(
            Arrays.asList(methodC, null, methodA, methodB, methodWithNullSignature));

    assertThat(sorted).hasSize(4);
    assertThat(sorted.get(0).getSignature()).isNull();
    assertThat(sorted.get(1).getName()).isEqualTo("alpha");
    assertThat(sorted.get(2).getName()).isEqualTo("zeta");
    assertThat(sorted.get(3).getSignature()).isEqualTo("b()");
  }

  @Test
  void sortAndJoin_shouldSortBeforeJoin() {
    List<String> input = Arrays.asList("c", "a", "b");

    String joined = PromptInputCanonicalizer.sortAndJoin(input, "-");

    assertThat(joined).isEqualTo("a-b-c");
  }

  @Test
  void sortAndJoin_collection_shouldUseCollectionOverloadAndDropNulls() {
    Collection<String> input = Arrays.asList("z", null, "a", "m");

    String joined = PromptInputCanonicalizer.sortAndJoin(input, "|");

    assertThat(joined).isEqualTo("a|m|z");
  }

  @Test
  void sortMethods_shouldTreatNullMethodNameAsEmptyStringForTieBreak() {
    MethodInfo nullName = new MethodInfo();
    nullName.setSignature("same()");
    nullName.setName(null);

    MethodInfo named = new MethodInfo();
    named.setSignature("same()");
    named.setName("alpha");

    List<MethodInfo> sorted = PromptInputCanonicalizer.sortMethods(Arrays.asList(named, nullName));

    assertThat(sorted).containsExactly(nullName, named);
  }

  @Test
  void nullInputs_shouldReturnEmptyLists() {
    assertThat(PromptInputCanonicalizer.sortStrings((List<String>) null)).isEmpty();
    assertThat(PromptInputCanonicalizer.sortStrings((Collection<String>) null)).isEmpty();
    assertThat(PromptInputCanonicalizer.sortMethods(null)).isEmpty();
    assertThat(PromptInputCanonicalizer.sortStrings(List.of())).isEmpty();
    assertThat(PromptInputCanonicalizer.sortStrings(Set.of())).isEmpty();
    assertThat(PromptInputCanonicalizer.sortMethods(List.of())).isEmpty();
  }
}
