package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MethodInfoTest {

  @Test
  void getCalledMethods_returnsEmptyWhenUnset() {
    MethodInfo info = new MethodInfo();

    assertThat(info.getCalledMethods()).isEmpty();
  }

  @Test
  void setCalledMethodRefs_derivesCalledMethodsFromResolvedOrRaw() {
    MethodInfo info = new MethodInfo();

    CalledMethodRef resolved = new CalledMethodRef();
    resolved.setRaw("raw#call");
    resolved.setResolved("resolved#call");
    resolved.setStatus(ResolutionStatus.RESOLVED);

    CalledMethodRef rawOnly = new CalledMethodRef();
    rawOnly.setRaw("rawOnly#call");
    rawOnly.setStatus(ResolutionStatus.UNRESOLVED);

    info.setCalledMethodRefs(List.of(resolved, rawOnly));

    assertThat(info.getCalledMethods()).containsExactly("resolved#call", "rawOnly#call");
  }

  @Test
  void setCalledMethods_buildsLegacyCalledMethodRefs() {
    MethodInfo info = new MethodInfo();

    info.setCalledMethods(List.of("com.example.Foo#bar", "unknown#baz"));

    assertThat(info.getCalledMethodRefs()).hasSize(2);

    CalledMethodRef first = info.getCalledMethodRefs().get(0);
    assertThat(first.getRaw()).isEqualTo("com.example.Foo#bar");
    assertThat(first.getResolved()).isEqualTo("com.example.Foo#bar");
    assertThat(first.getStatus()).isEqualTo(ResolutionStatus.RESOLVED);
    assertThat(first.getConfidence()).isEqualTo(1.0);
    assertThat(first.getSource()).isEqualTo("legacy");

    CalledMethodRef second = info.getCalledMethodRefs().get(1);
    assertThat(second.getRaw()).isEqualTo("unknown#baz");
    assertThat(second.getResolved()).isNull();
    assertThat(second.getStatus()).isEqualTo(ResolutionStatus.UNRESOLVED);
    assertThat(second.getConfidence()).isEqualTo(0.3);
    assertThat(second.getSource()).isEqualTo("legacy");
  }

  @Test
  void setCalledMethods_handlesNullEntriesAsUnresolved() {
    MethodInfo info = new MethodInfo();

    List<String> legacyCalls = new ArrayList<>();
    legacyCalls.add("unknown");
    legacyCalls.add(null);

    info.setCalledMethods(legacyCalls);

    assertThat(info.getCalledMethodRefs()).hasSize(2);
    assertThat(info.getCalledMethodRefs().get(0).getStatus())
        .isEqualTo(ResolutionStatus.UNRESOLVED);
    assertThat(info.getCalledMethodRefs().get(0).getConfidence()).isEqualTo(0.3);
    assertThat(info.getCalledMethodRefs().get(1).getRaw()).isNull();
    assertThat(info.getCalledMethodRefs().get(1).getStatus())
        .isEqualTo(ResolutionStatus.UNRESOLVED);
  }

  @Test
  void setCalledMethodRefs_withNullResetsDerivedCalledMethods() {
    MethodInfo info = new MethodInfo();
    info.setCalledMethods(List.of("com.example.Foo#bar"));

    info.setCalledMethodRefs(null);

    assertThat(info.getCalledMethodRefs()).isEmpty();
    assertThat(info.getCalledMethods()).isEmpty();
  }

  @Test
  void setCalledMethods_isIgnoredWhenRefsAlreadyExist() {
    MethodInfo info = new MethodInfo();

    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw("raw#call");
    info.setCalledMethodRefs(List.of(ref));

    info.setCalledMethods(List.of("ignored#call"));

    assertThat(info.getCalledMethods()).containsExactly("raw#call");
  }

  @Test
  void setCalledMethodRefs_dedupesWhitespaceAndInnerClassDelimiterVariants() {
    MethodInfo info = new MethodInfo();

    CalledMethodRef first = new CalledMethodRef();
    first.setResolved("com.example.Foo$Inner#call(java.lang.String, int)");
    first.setStatus(ResolutionStatus.RESOLVED);

    CalledMethodRef second = new CalledMethodRef();
    second.setResolved("com.example.Foo.Inner#call(java.lang.String,int)");
    second.setStatus(ResolutionStatus.RESOLVED);

    info.setCalledMethodRefs(List.of(first, second));

    assertThat(info.getCalledMethods())
        .containsExactly("com.example.Foo$Inner#call(java.lang.String, int)");
  }

  @Test
  void representativePaths_areCopiedAndUnmodifiable() {
    MethodInfo info = new MethodInfo();
    List<RepresentativePath> paths = new ArrayList<>();
    paths.add(new RepresentativePath());

    info.setRepresentativePaths(paths);
    paths.add(new RepresentativePath());

    assertThat(info.getRepresentativePaths()).hasSize(1);
    assertThatThrownBy(() -> info.getRepresentativePaths().add(new RepresentativePath()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void brittlenessSignals_updateBrittleFlag() {
    MethodInfo info = new MethodInfo();

    info.setBrittlenessSignals(List.of(BrittlenessSignal.TIME));
    assertThat(info.isBrittle()).isTrue();

    info.setBrittlenessSignals(List.of());
    assertThat(info.isBrittle()).isFalse();
  }

  @Test
  void dynamicFeatureTotal_sumsCounts() {
    MethodInfo info = new MethodInfo();

    info.setDynamicFeatureHigh(1);
    info.setDynamicFeatureMedium(2);
    info.setDynamicFeatureLow(3);

    assertThat(info.getDynamicFeatureTotal()).isEqualTo(6);
  }

  @Test
  void listSetters_areNullSafeAndListGettersAreUnmodifiable() {
    MethodInfo info = new MethodInfo();

    info.setRawSignatures(null);
    info.setAnnotations(null);
    info.setThrownExceptions(null);
    info.setDynamicResolutions(null);
    info.setBrittlenessSignals(null);

    assertThat(info.getRawSignatures()).isEmpty();
    assertThat(info.getAnnotations()).isEmpty();
    assertThat(info.getThrownExceptions()).isEmpty();
    assertThat(info.getDynamicResolutions()).isEmpty();
    assertThat(info.getBrittlenessSignals()).isEmpty();
    assertThatThrownBy(() -> info.getRawSignatures().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> info.getAnnotations().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> info.getThrownExceptions().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> info.getDynamicResolutions().add(DynamicResolution.builder().build()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> info.getBrittlenessSignals().add(BrittlenessSignal.TIME))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void simpleScalarSetters_roundTrip() {
    MethodInfo info = new MethodInfo();

    info.setPartOfCycle(true);
    info.setDeadCode(true);
    info.setDuplicate(true);
    info.setDuplicateGroup("group-a");
    info.setCodeHash("hash");
    info.setMaxNestingDepth(4);
    info.setParameterCount(2);
    info.setUsageCount(9);
    info.setHasLoops(true);
    info.setHasConditionals(true);
    info.setSourceCode("return 1;");
    info.setStatic(true);
    info.setDynamicFeatureHasServiceLoader(true);
    info.setBrittle(true);

    assertThat(info.isPartOfCycle()).isTrue();
    assertThat(info.isDeadCode()).isTrue();
    assertThat(info.isDuplicate()).isTrue();
    assertThat(info.getDuplicateGroup()).isEqualTo("group-a");
    assertThat(info.getCodeHash()).isEqualTo("hash");
    assertThat(info.getMaxNestingDepth()).isEqualTo(4);
    assertThat(info.getParameterCount()).isEqualTo(2);
    assertThat(info.getUsageCount()).isEqualTo(9);
    assertThat(info.hasLoops()).isTrue();
    assertThat(info.hasConditionals()).isTrue();
    assertThat(info.getSourceCode()).isEqualTo("return 1;");
    assertThat(info.isStatic()).isTrue();
    assertThat(info.hasDynamicFeatureServiceLoader()).isTrue();
    assertThat(info.isBrittle()).isTrue();
  }
}
