package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisError;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.BranchSummary;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.BrittlenessSignal;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.GuardSummary;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.GuardType;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.RepresentativePath;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelFreezerTest {

  @Test
  void freezeAnalysisResult_detachesCollectionsAndMakesUnmodifiable() {
    List<String> methodAnnotations = new ArrayList<>(List.of("MethodAnno"));
    List<String> calledCandidates = new ArrayList<>(List.of("com.example.Foo#bar()"));
    List<String> rawSignatures = new ArrayList<>(List.of("bar()"));
    List<String> thrown = new ArrayList<>(List.of("IOException"));
    List<BrittlenessSignal> signals = new ArrayList<>(List.of(BrittlenessSignal.TIME));
    List<String> guardEffects = new ArrayList<>(List.of("effect"));
    GuardSummary guard = new GuardSummary();
    guard.setType(GuardType.LEGACY);
    guard.setCondition("x > 0");
    guard.setEffects(guardEffects);

    BranchSummary branchSummary = new BranchSummary();
    List<GuardSummary> guards = new ArrayList<>(List.of(guard));
    branchSummary.setGuards(guards);
    branchSummary.setSwitches(new ArrayList<>(List.of("switch")));
    branchSummary.setPredicates(new ArrayList<>(List.of("pred")));

    List<String> repConditions = new ArrayList<>(List.of("cond"));
    RepresentativePath rep = new RepresentativePath();
    rep.setId("rp1");
    rep.setRequiredConditions(repConditions);

    CalledMethodRef ref = new CalledMethodRef();
    ref.setRaw("com.example.Foo#bar()");
    ref.setStatus(ResolutionStatus.UNRESOLVED);
    ref.setCandidates(calledCandidates);
    ref.setArgumentLiterals(new ArrayList<>(List.of("\"literal\"")));

    MethodInfo method = new MethodInfo();
    method.setName("bar");
    method.setSignature("bar()");
    method.setAnnotations(methodAnnotations);
    method.setCalledMethodRefs(new ArrayList<>(List.of(ref)));
    method.setRawSignatures(rawSignatures);
    method.setThrownExceptions(thrown);
    method.setBrittlenessSignals(signals);
    method.setBranchSummary(branchSummary);
    method.setRepresentativePaths(new ArrayList<>(List.of(rep)));

    List<MethodInfo> methods = new ArrayList<>(List.of(method));
    List<String> classAnnotations = new ArrayList<>(List.of("ClassAnno"));
    List<FieldInfo> fields = new ArrayList<>(List.of(new FieldInfo()));
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.Foo");
    cls.setMethods(methods);
    cls.setAnnotations(classAnnotations);
    cls.setFields(fields);
    cls.setExtendsTypes(new ArrayList<>(List.of("Base")));
    cls.setImplementsTypes(new ArrayList<>(List.of("Runnable")));
    cls.setImports(new ArrayList<>(List.of("java.util.List")));

    AnalysisResult result = new AnalysisResult();
    List<ClassInfo> classes = new ArrayList<>(List.of(cls));
    List<AnalysisError> errors = new ArrayList<>(List.of(new AnalysisError("Foo.java", "oops", 1)));
    result.setClasses(classes);
    result.setAnalysisErrors(errors);

    ModelFreezer.freezeAnalysisResult(result);

    classes.add(new ClassInfo());
    errors.add(new AnalysisError("Other.java", "err", 2));
    classAnnotations.add("NewAnno");
    methods.add(new MethodInfo());
    methodAnnotations.add("NewMethodAnno");
    calledCandidates.add("new");
    rawSignatures.add("other()");
    thrown.add("RuntimeException");
    signals.add(BrittlenessSignal.IO);
    guardEffects.add("new-effect");
    repConditions.add("new-cond");

    assertThat(result.getClasses()).hasSize(1);
    assertThat(result.getAnalysisErrors()).hasSize(1);
    assertThat(cls.getAnnotations()).containsExactly("ClassAnno");
    assertThat(cls.getMethods()).hasSize(1);
    assertThat(method.getAnnotations()).containsExactly("MethodAnno");
    assertThat(method.getRawSignatures()).containsExactly("bar()");
    assertThat(method.getThrownExceptions()).containsExactly("IOException");
    assertThat(method.getBrittlenessSignals()).containsExactly(BrittlenessSignal.TIME);
    assertThat(method.getCalledMethodRefs()).hasSize(1);
    assertThat(method.getCalledMethodRefs().getFirst().getCandidates())
        .containsExactly("com.example.Foo#bar()");
    assertThat(method.getCalledMethodRefs().getFirst().getArgumentLiterals())
        .containsExactly("\"literal\"");
    assertThat(method.getCalledMethodRefs().getFirst()).isNotSameAs(ref);
    assertThat(method.getBranchSummary()).isNotSameAs(branchSummary);
    assertThat(method.getBranchSummary().getGuards()).hasSize(1);
    assertThat(method.getBranchSummary().getGuards().getFirst().getEffects())
        .containsExactly("effect");
    assertThat(method.getRepresentativePaths()).hasSize(1);
    assertThat(method.getRepresentativePaths().getFirst()).isNotSameAs(rep);
    assertThat(method.getRepresentativePaths().getFirst().getRequiredConditions())
        .containsExactly("cond");

    assertThatThrownBy(() -> result.getClasses().add(new ClassInfo()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> result.getAnalysisErrors().add(new AnalysisError("File.java", "msg", 3)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void freezeMethodInfo_handlesAbsentBranchSummary() {
    MethodInfo method = new MethodInfo();
    method.setBranchSummary(null);

    MethodInfo frozen = ModelFreezer.freezeMethodInfo(method);

    assertThat(frozen.getBranchSummary()).isNull();
  }

  @Test
  void freezeHelpers_returnNullForNullInputs() {
    assertThat((Object) invokePrivate("freezeCalledMethodRef", CalledMethodRef.class, null))
        .isNull();
    assertThat((Object) invokePrivate("freezeGuardSummary", GuardSummary.class, null)).isNull();
    assertThat((Object) invokePrivate("freezeRepresentativePath", RepresentativePath.class, null))
        .isNull();
  }

  private static <T> T invokePrivate(String methodName, Class<?> parameterType, Object argument) {
    try {
      Method method = ModelFreezer.class.getDeclaredMethod(methodName, parameterType);
      method.setAccessible(true);
      return (T) method.invoke(null, argument);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}
