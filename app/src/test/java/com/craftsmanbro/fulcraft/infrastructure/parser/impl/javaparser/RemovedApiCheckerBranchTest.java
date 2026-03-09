package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedArrayType;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedWildcard;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemovedApiCheckerBranchTest {

  @Test
  void isRemovedApiUsage_detectsAcrossAdditionalNodeKinds() {
    String source =
        """
        package com.example;

        import javax.xml.bind.JAXBContext;
        import javax.xml.bind.annotation.XmlRootElement;

        @XmlRootElement
        class Sample {
          void use() {
            new JAXBContext();
            JAXBContext.newInstance("x");
            JAXBContext.VALUE.toString();
            Class<?> t = JAXBContext.class;
            JAXBContext direct = null;
            direct.toString();
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    RemovedApiDetector.RemovedApiImportInfo importInfo =
        RemovedApiDetector.fromImports(toImportStrings(cu));

    AnnotationExpr annotation = cu.findFirst(AnnotationExpr.class).orElseThrow();
    ObjectCreationExpr objectCreation = cu.findFirst(ObjectCreationExpr.class).orElseThrow();
    MethodCallExpr methodCall =
        cu.findAll(MethodCallExpr.class).stream()
            .filter(
                call ->
                    call.getScope().map(Object::toString).filter("JAXBContext"::equals).isPresent())
            .findFirst()
            .orElseThrow();
    FieldAccessExpr fieldAccess =
        cu.findAll(FieldAccessExpr.class).stream()
            .filter(fa -> "VALUE".equals(fa.getNameAsString()))
            .findFirst()
            .orElseThrow();
    ClassExpr classExpr = cu.findFirst(ClassExpr.class).orElseThrow();
    NameExpr nameExpr =
        cu.findAll(NameExpr.class).stream()
            .filter(name -> "JAXBContext".equals(name.getNameAsString()))
            .findFirst()
            .orElseThrow();
    Type type =
        cu.findAll(Type.class).stream()
            .filter(t -> "JAXBContext".equals(t.asString()))
            .findFirst()
            .orElseThrow();

    assertTrue(RemovedApiChecker.isRemovedApiUsage(annotation, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(objectCreation, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(methodCall, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(fieldAccess, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(classExpr, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(nameExpr, importInfo));
    assertTrue(RemovedApiChecker.isRemovedApiUsage(type, importInfo));
    assertFalse(
        RemovedApiChecker.isRemovedApiUsage(cu.getPackageDeclaration().orElseThrow(), importInfo));
  }

  @Test
  void isRemovedApiUsage_returnsFalseForUnresolvedNameExpr() {
    String source =
        """
        package com.example;

        class Sample {
          void use() {
            unknownVar.toString();
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    NameExpr unknown =
        cu.findAll(NameExpr.class).stream()
            .filter(name -> "unknownVar".equals(name.getNameAsString()))
            .findFirst()
            .orElseThrow();
    RemovedApiDetector.RemovedApiImportInfo importInfo =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.JAXBContext"));

    assertFalse(RemovedApiChecker.isRemovedApiUsage(unknown, importInfo));
  }

  @Test
  void resolvedTypeHelpers_handleArrayReferenceWildcardAndFallbackBranches() throws Exception {
    RemovedApiDetector.RemovedApiImportInfo importInfo =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.JAXBContext"));

    Method matchesResolvedType =
        RemovedApiChecker.class.getDeclaredMethod(
            "matchesResolvedType",
            ResolvedType.class,
            RemovedApiDetector.RemovedApiImportInfo.class);
    matchesResolvedType.setAccessible(true);

    Method matchesReferenceType =
        RemovedApiChecker.class.getDeclaredMethod(
            "matchesReferenceType",
            ResolvedReferenceType.class,
            RemovedApiDetector.RemovedApiImportInfo.class);
    matchesReferenceType.setAccessible(true);

    Method matchesReferenceTypeRecursively =
        RemovedApiChecker.class.getDeclaredMethod(
            "matchesReferenceTypeRecursively",
            ResolvedReferenceType.class,
            RemovedApiDetector.RemovedApiImportInfo.class);
    matchesReferenceTypeRecursively.setAccessible(true);

    Method matchesWildcardType =
        RemovedApiChecker.class.getDeclaredMethod(
            "matchesWildcardType",
            ResolvedWildcard.class,
            RemovedApiDetector.RemovedApiImportInfo.class);
    matchesWildcardType.setAccessible(true);

    ResolvedReferenceType jaxbReference = mock(ResolvedReferenceType.class);
    when(jaxbReference.getQualifiedName()).thenReturn("com.example.JAXBContext");
    when(jaxbReference.describe()).thenReturn("com.example.JAXBContext");
    when(jaxbReference.typeParametersValues()).thenReturn(List.of());

    ResolvedType referenceType = mock(ResolvedType.class);
    when(referenceType.isArray()).thenReturn(false);
    when(referenceType.isReferenceType()).thenReturn(true);
    when(referenceType.asReferenceType()).thenReturn(jaxbReference);
    when(referenceType.isWildcard()).thenReturn(false);

    ResolvedArrayType arrayType = new ResolvedArrayType(referenceType);
    ResolvedWildcard boundedWildcard = ResolvedWildcard.extendsBound(referenceType);
    ResolvedWildcard unboundedWildcard = ResolvedWildcard.UNBOUNDED;

    ResolvedType primitiveLike = mock(ResolvedType.class);
    when(primitiveLike.isArray()).thenReturn(false);
    when(primitiveLike.isReferenceType()).thenReturn(false);
    when(primitiveLike.isWildcard()).thenReturn(false);

    assertFalse(invokeBoolean(matchesResolvedType, null, importInfo));
    assertTrue(invokeBoolean(matchesResolvedType, arrayType, importInfo));
    assertTrue(invokeBoolean(matchesResolvedType, referenceType, importInfo));
    assertTrue(invokeBoolean(matchesResolvedType, boundedWildcard, importInfo));
    assertFalse(invokeBoolean(matchesResolvedType, unboundedWildcard, importInfo));
    assertFalse(invokeBoolean(matchesResolvedType, primitiveLike, importInfo));

    ResolvedReferenceType removedPackageRef = mock(ResolvedReferenceType.class);
    when(removedPackageRef.getQualifiedName()).thenReturn("javax.xml.ws.Service");
    when(removedPackageRef.describe()).thenReturn("javax.xml.ws.Service");
    when(removedPackageRef.typeParametersValues()).thenReturn(List.of());
    assertTrue(invokeBoolean(matchesReferenceType, removedPackageRef, importInfo));

    ResolvedReferenceType describedOnlyRef = mock(ResolvedReferenceType.class);
    when(describedOnlyRef.getQualifiedName()).thenReturn("com.example.NonRemoved");
    when(describedOnlyRef.describe()).thenReturn("com.example.JAXBContext");
    when(describedOnlyRef.typeParametersValues()).thenReturn(List.of());
    assertTrue(invokeBoolean(matchesReferenceType, describedOnlyRef, importInfo));

    ResolvedReferenceType nonMatchRef = mock(ResolvedReferenceType.class);
    when(nonMatchRef.getQualifiedName()).thenReturn("NoDotName");
    when(nonMatchRef.describe()).thenReturn("NoDotName");
    when(nonMatchRef.typeParametersValues()).thenReturn(List.of());
    assertFalse(invokeBoolean(matchesReferenceType, nonMatchRef, importInfo));
    assertFalse(invokeBoolean(matchesReferenceType, null, importInfo));

    ResolvedReferenceType genericRef = mock(ResolvedReferenceType.class);
    when(genericRef.getQualifiedName()).thenReturn("com.example.List");
    when(genericRef.describe()).thenReturn("com.example.List");
    when(genericRef.typeParametersValues())
        .thenReturn(List.of(ResolvedWildcard.extendsBound(referenceType)));
    assertTrue(invokeBoolean(matchesReferenceTypeRecursively, genericRef, importInfo));

    ResolvedReferenceType genericNoMatchRef = mock(ResolvedReferenceType.class);
    when(genericNoMatchRef.getQualifiedName()).thenReturn("com.example.List");
    when(genericNoMatchRef.describe()).thenReturn("com.example.List");
    when(genericNoMatchRef.typeParametersValues()).thenReturn(List.of(unboundedWildcard));
    assertFalse(invokeBoolean(matchesReferenceTypeRecursively, genericNoMatchRef, importInfo));

    assertTrue(invokeBoolean(matchesWildcardType, boundedWildcard, importInfo));
    assertFalse(invokeBoolean(matchesWildcardType, unboundedWildcard, importInfo));
  }

  private static boolean invokeBoolean(Method method, Object... args) throws Exception {
    return (Boolean) method.invoke(null, args);
  }

  private static List<String> toImportStrings(CompilationUnit cu) {
    return cu.getImports().stream()
        .map(
            i -> {
              String name = i.getNameAsString();
              if (i.isAsterisk()) {
                name = name + ".*";
              }
              if (i.isStatic()) {
                name = "static " + name;
              }
              return name;
            })
        .toList();
  }
}
