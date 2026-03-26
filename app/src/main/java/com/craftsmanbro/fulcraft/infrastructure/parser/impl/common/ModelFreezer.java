package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.BranchSummary;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.CalledMethodRef;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.GuardSummary;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.RepresentativePath;
import java.util.List;
import java.util.Objects;

/**
 * Utility to freeze mutable DTO graphs at layer boundaries.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.ModelFreezer} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class ModelFreezer {

  private ModelFreezer() {}

  public static AnalysisResult freezeAnalysisResult(final AnalysisResult result) {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "AnalysisResult must not be null"));
    result.getClasses().forEach(ModelFreezer::freezeClassInfo);
    result.setClasses(unmodifiableCopy(result.getClasses()));
    result.setAnalysisErrors(unmodifiableCopy(result.getAnalysisErrors()));
    return result;
  }

  public static ClassInfo freezeClassInfo(final ClassInfo cls) {
    Objects.requireNonNull(
        cls,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "ClassInfo must not be null"));
    cls.setExtendsTypes(unmodifiableCopy(cls.getExtendsTypes()));
    cls.setImplementsTypes(unmodifiableCopy(cls.getImplementsTypes()));
    cls.setFields(unmodifiableCopy(cls.getFields()));
    cls.setAnnotations(unmodifiableCopy(cls.getAnnotations()));
    cls.setImports(unmodifiableCopy(cls.getImports()));
    cls.getMethods().forEach(ModelFreezer::freezeMethodInfo);
    cls.setMethods(unmodifiableCopy(cls.getMethods()));
    return cls;
  }

  public static MethodInfo freezeMethodInfo(final MethodInfo mi) {
    Objects.requireNonNull(
        mi,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "MethodInfo must not be null"));
    mi.setAnnotations(unmodifiableCopy(mi.getAnnotations()));
    mi.setCalledMethodRefs(
        unmodifiableCopy(mi.getCalledMethodRefs()).stream()
            .map(ModelFreezer::freezeCalledMethodRef)
            .toList());
    mi.setCalledMethods(unmodifiableCopy(mi.getCalledMethods()));
    mi.setThrownExceptions(unmodifiableCopy(mi.getThrownExceptions()));
    mi.setRawSignatures(unmodifiableCopy(mi.getRawSignatures()));
    mi.setBrittlenessSignals(unmodifiableCopy(mi.getBrittlenessSignals()));
    if (mi.getBranchSummary() != null) {
      final BranchSummary bs = new BranchSummary();
      bs.setGuards(
          unmodifiableCopy(mi.getBranchSummary().getGuards()).stream()
              .map(ModelFreezer::freezeGuardSummary)
              .toList());
      bs.setSwitches(unmodifiableCopy(mi.getBranchSummary().getSwitches()));
      bs.setPredicates(unmodifiableCopy(mi.getBranchSummary().getPredicates()));
      mi.setBranchSummary(bs);
    }
    mi.setRepresentativePaths(
        mi.getRepresentativePaths().stream().map(ModelFreezer::freezeRepresentativePath).toList());
    return mi;
  }

  private static CalledMethodRef freezeCalledMethodRef(final CalledMethodRef ref) {
    if (ref == null) {
      return null;
    }
    final CalledMethodRef frozen = new CalledMethodRef();
    frozen.setRaw(ref.getRaw());
    frozen.setResolved(ref.getResolved());
    frozen.setStatus(ref.getStatus());
    frozen.setConfidence(ref.getConfidence());
    frozen.setSource(ref.getSource());
    frozen.setCandidates(unmodifiableCopy(ref.getCandidates()));
    frozen.setArgumentLiterals(unmodifiableCopy(ref.getArgumentLiterals()));
    return frozen;
  }

  private static GuardSummary freezeGuardSummary(final GuardSummary guard) {
    if (guard == null) {
      return null;
    }
    final GuardSummary frozen = new GuardSummary();
    frozen.setType(guard.getType());
    frozen.setCondition(guard.getCondition());
    frozen.setEffects(unmodifiableCopy(guard.getEffects()));
    frozen.setMessageLiteral(guard.getMessageLiteral());
    frozen.setLocation(guard.getLocation());
    return frozen;
  }

  private static RepresentativePath freezeRepresentativePath(final RepresentativePath path) {
    if (path == null) {
      return null;
    }
    final RepresentativePath frozen = new RepresentativePath();
    frozen.setId(path.getId());
    frozen.setDescription(path.getDescription());
    frozen.setExpectedOutcomeHint(path.getExpectedOutcomeHint());
    frozen.setRequiredConditions(unmodifiableCopy(path.getRequiredConditions()));
    return frozen;
  }

  private static <T> List<T> unmodifiableCopy(final List<T> list) {
    if (list == null) {
      return List.of();
    }
    return List.copyOf(list);
  }
}
