package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtType;

/** Spoon-specific helpers for mock hint analysis. */
public final class SpoonHelper {

  private SpoonHelper() {
    // Utility class
  }

  public static Set<String> collectInterfaceNames(final CtType<?> type) {
    final Set<String> interfaces = new HashSet<>();
    for (final CtType<?> candidate : collectSameFileTypes(type)) {
      if (candidate.equals(type)) {
        continue;
      }
      if (candidate.isInterface()) {
        interfaces.add(candidate.getSimpleName());
      }
    }
    return interfaces;
  }

  public static Set<String> collectAbstractClassNames(final CtType<?> type) {
    final Set<String> abstractClasses = new HashSet<>();
    for (final CtType<?> candidate : collectSameFileTypes(type)) {
      if (candidate.equals(type)) {
        continue;
      }
      if (candidate instanceof CtClass<?> ctClass && ctClass.isAbstract()) {
        abstractClasses.add(candidate.getSimpleName());
      }
    }
    return abstractClasses;
  }

  public static Set<String> collectConstructorParamTypes(final CtType<?> type) {
    final Set<String> paramTypes = new HashSet<>();
    for (final CtConstructor<?> constructor : getConstructors(type)) {
      constructor.getParameters().stream()
          .filter(param -> param.getType() != null)
          .forEach(param -> paramTypes.add(param.getType().getSimpleName()));
    }
    return paramTypes;
  }

  private static List<CtConstructor<?>> getConstructors(final CtType<?> type) {
    final List<CtConstructor<?>> constructors = new ArrayList<>();
    if (type instanceof CtClass<?> ctClass) {
      constructors.addAll(ctClass.getConstructors());
    }
    if (type instanceof CtEnum<?> ctEnum) {
      constructors.addAll(ctEnum.getConstructors());
    }
    return constructors;
  }

  private static Set<CtType<?>> collectSameFileTypes(final CtType<?> type) {
    final CtCompilationUnit compilationUnit =
        type.getPosition() != null ? type.getPosition().getCompilationUnit() : null;
    final List<CtType<?>> declaredTypes =
        compilationUnit != null ? compilationUnit.getDeclaredTypes() : List.of(type);
    final Set<CtType<?>> allTypes = new HashSet<>();
    for (final CtType<?> declared : declaredTypes) {
      collectTypeRecursively(declared, allTypes);
    }
    return allTypes;
  }

  private static void collectTypeRecursively(final CtType<?> type, final Set<CtType<?>> collector) {
    if (type == null || !collector.add(type)) {
      return;
    }
    for (final CtType<?> nested : type.getNestedTypes()) {
      collectTypeRecursively(nested, collector);
    }
  }
}
