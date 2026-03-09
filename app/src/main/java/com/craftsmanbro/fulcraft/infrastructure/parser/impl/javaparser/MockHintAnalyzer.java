package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MockHintStrategy;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Analyzes fields to determine if they require mocking in tests.
 *
 * <p>Uses a combination of: 1. Same-file type analysis (interfaces/abstract classes defined in the
 * same file) 2. Constructor injection detection (DI pattern) 3. Naming convention heuristics
 * (Repository, Gateway, Service, etc.)
 */
public final class MockHintAnalyzer {

  private MockHintAnalyzer() {
    // Utility class
  }

  /**
   * Collects interface names defined in a class (inner interfaces).
   *
   * @param classDecl the class declaration to analyze
   * @return set of interface names
   */
  public static Set<String> collectInterfaceNames(final ClassOrInterfaceDeclaration classDecl) {
    final Set<String> interfaces = new HashSet<>();
    collectSameFileTypes(classDecl).stream()
        .filter(c -> !isSameDeclaration(c, classDecl))
        .filter(ClassOrInterfaceDeclaration::isInterface)
        .forEach(c -> interfaces.add(c.getNameAsString()));
    return interfaces;
  }

  /**
   * Collects abstract class names defined in a class (inner abstract classes).
   *
   * @param classDecl the class declaration to analyze
   * @return set of abstract class names
   */
  public static Set<String> collectAbstractClassNames(final ClassOrInterfaceDeclaration classDecl) {
    final Set<String> abstractClasses = new HashSet<>();
    collectSameFileTypes(classDecl).stream()
        .filter(c -> !isSameDeclaration(c, classDecl))
        .filter(c -> !c.isInterface() && c.isAbstract())
        .forEach(c -> abstractClasses.add(c.getNameAsString()));
    return abstractClasses;
  }

  /**
   * Collects parameter type names from all constructors of a class.
   *
   * @param classDecl the class declaration to analyze
   * @return set of parameter type names
   */
  public static Set<String> collectConstructorParamTypes(
      final ClassOrInterfaceDeclaration classDecl) {
    final Set<String> paramTypes = new HashSet<>();
    final List<ConstructorDeclaration> constructors = classDecl.getConstructors();
    for (final ConstructorDeclaration constructor : constructors) {
      for (final Parameter param : constructor.getParameters()) {
        paramTypes.add(param.getType().asString());
      }
    }
    return paramTypes;
  }

  /**
   * Analyzes all fields of a class and sets mock hints.
   *
   * @param classDecl the class declaration containing fields
   * @param fields the list of FieldInfo to update
   */
  public static void analyzeFields(
      final ClassOrInterfaceDeclaration classDecl, final List<FieldInfo> fields) {
    final Set<String> interfaceNames = collectInterfaceNames(classDecl);
    final Set<String> abstractClassNames = collectAbstractClassNames(classDecl);
    final Set<String> constructorParamTypes = collectConstructorParamTypes(classDecl);
    for (final FieldInfo field : fields) {
      final String mockHint =
          MockHintStrategy.determineMockHint(
              field, interfaceNames, abstractClassNames, constructorParamTypes);
      field.setMockHint(mockHint);
    }
  }

  private static List<ClassOrInterfaceDeclaration> collectSameFileTypes(
      final ClassOrInterfaceDeclaration classDecl) {
    return classDecl
        .findCompilationUnit()
        .map(cu -> cu.findAll(ClassOrInterfaceDeclaration.class))
        .orElseGet(() -> classDecl.findAll(ClassOrInterfaceDeclaration.class));
  }

  private static boolean isSameDeclaration(
      final ClassOrInterfaceDeclaration left, final ClassOrInterfaceDeclaration right) {
    return Objects.equals(declarationKey(left), declarationKey(right));
  }

  private static String declarationKey(final ClassOrInterfaceDeclaration declaration) {
    return declaration
        .getFullyQualifiedName()
        .orElseGet(
            () ->
                declaration
                    .getRange()
                    .map(range -> range.begin + ":" + range.end)
                    .orElseGet(declaration::getNameAsString));
  }
}
