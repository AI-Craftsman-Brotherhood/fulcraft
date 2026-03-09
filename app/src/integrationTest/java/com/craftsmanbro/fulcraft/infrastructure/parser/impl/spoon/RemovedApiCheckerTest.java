package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

class RemovedApiCheckerTest {

  @TempDir Path tempDir;

  @Test
  void detectsRemovedApiUsageWithImportInfo() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        import javax.xml.bind.JAXBContext;

                        public class Sample {
                            public void build() throws Exception {
                                JAXBContext.newInstance("x");
                            }
                        }
                        """);
    CtMethod<?> build =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("build").get(0);

    var info = RemovedApiDetector.fromImports(List.of("javax.xml.bind.JAXBContext"));

    assertTrue(RemovedApiChecker.usesRemovedApis(build, info));
  }

  @Test
  void returnsFalseWhenNoRemovedApiUsage() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        public class Sample {
                            public void now() {
                                java.time.Instant.now();
                            }
                        }
                        """);
    CtMethod<?> now =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("now").get(0);

    assertFalse(RemovedApiChecker.usesRemovedApis(now, null));
  }

  @Test
  void detectsRemovedApiUsageInFieldAccess() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        import javax.xml.bind.Marshaller;

                        public class Sample {
                            public void useField() {
                                String key = Marshaller.JAXB_ENCODING;
                            }
                        }
                        """);
    CtMethod<?> useField =
        SpoonTestUtils.getType(launcher, "com.example.Sample").getMethodsByName("useField").get(0);
    var info = RemovedApiDetector.fromImports(List.of("javax.xml.bind.Marshaller"));

    assertTrue(RemovedApiChecker.usesRemovedApis(useField, info));
  }

  @Test
  void detectsRemovedApiAnnotationAndWildcardBounds() throws Exception {
    Launcher launcher =
        SpoonTestUtils.buildLauncher(
            tempDir,
            "src/main/java/com/example/Sample.java",
            """
                        package com.example;

                        @javax.xml.bind.annotation.XmlRootElement
                        public class Sample {
                            java.util.List<? extends javax.xml.bind.JAXBElement> values;
                        }
                        """);
    var sampleType = SpoonTestUtils.getType(launcher, "com.example.Sample");
    var annotation = sampleType.getAnnotations().get(0);
    var fieldType = sampleType.getField("values").getType();

    assertTrue(
        RemovedApiChecker.isRemovedApiElement(
            annotation, new RemovedApiDetector.RemovedApiImportInfo()));
    assertTrue(
        RemovedApiChecker.isRemovedApiElement(
            fieldType, new RemovedApiDetector.RemovedApiImportInfo()));
  }

  @Test
  void returnsFalseForNullElement() {
    assertFalse(
        RemovedApiChecker.isRemovedApiElement(null, new RemovedApiDetector.RemovedApiImportInfo()));
  }

  @Test
  void usesRemovedApisRejectsNullExecutable() {
    assertThrows(
        NullPointerException.class,
        () ->
            RemovedApiChecker.usesRemovedApis(null, new RemovedApiDetector.RemovedApiImportInfo()));
  }

  @Test
  void detectsRemovedApiViaInvocationAndFieldTargetsWhenReferencesMissing() {
    CtTypeReference<Object> removedType = mock(CtTypeReference.class);
    when(removedType.getQualifiedName()).thenReturn("javax.xml.bind.Marshaller");
    when(removedType.toString()).thenReturn("javax.xml.bind.Marshaller");
    when(removedType.getActualTypeArguments()).thenReturn(List.of());

    CtExpression<Object> targetExpr = mock(CtExpression.class);
    doReturn(removedType).when(targetExpr).getType();

    CtInvocation<Object> invocation = mock(CtInvocation.class);
    when(invocation.getExecutable()).thenReturn(null);
    doReturn(targetExpr).when(invocation).getTarget();

    CtFieldReference<Object> variable = mock(CtFieldReference.class);
    when(variable.getDeclaringType()).thenReturn(null);
    CtFieldAccess<Object> fieldAccess = mock(CtFieldAccess.class);
    when(fieldAccess.getVariable()).thenReturn(variable);
    doReturn(targetExpr).when(fieldAccess).getTarget();

    RemovedApiDetector.RemovedApiImportInfo info = new RemovedApiDetector.RemovedApiImportInfo();
    assertTrue(RemovedApiChecker.isRemovedApiElement(invocation, info));
    assertTrue(RemovedApiChecker.isRemovedApiElement(fieldAccess, info));
  }

  @Test
  void detectsRemovedApiFromWildcardBoundsAndTypeArguments() {
    CtTypeReference<Object> boundType = mock(CtTypeReference.class);
    when(boundType.getQualifiedName()).thenReturn("javax.xml.bind.JAXBElement");
    when(boundType.toString()).thenReturn("javax.xml.bind.JAXBElement");
    when(boundType.getActualTypeArguments()).thenReturn(List.of());

    CtWildcardReference wildcard = mock(CtWildcardReference.class);
    when(wildcard.getQualifiedName()).thenReturn(null);
    when(wildcard.toString()).thenReturn("? extends JAXBElement");
    doReturn(boundType).when(wildcard).getBoundingType();
    when(wildcard.getActualTypeArguments()).thenReturn(List.of());

    assertTrue(
        RemovedApiChecker.isRemovedApiElement(
            wildcard, new RemovedApiDetector.RemovedApiImportInfo()));

    CtTypeReference<Object> typeArg = mock(CtTypeReference.class);
    when(typeArg.getQualifiedName()).thenReturn("javax.xml.bind.JAXBContext");
    when(typeArg.toString()).thenReturn("javax.xml.bind.JAXBContext");
    when(typeArg.getActualTypeArguments()).thenReturn(List.of());

    CtTypeReference<Object> containerType = mock(CtTypeReference.class);
    when(containerType.getQualifiedName()).thenReturn(null);
    when(containerType.toString()).thenReturn("java.util.List");
    when(containerType.getActualTypeArguments()).thenReturn(List.of(typeArg));

    assertTrue(
        RemovedApiChecker.isRemovedApiElement(
            containerType, new RemovedApiDetector.RemovedApiImportInfo()));
  }

  @Test
  void returnsFalseForNonRemovedTypesAcrossInvocationAndFieldAccess() {
    CtTypeReference<Object> safeType = mock(CtTypeReference.class);
    when(safeType.getQualifiedName()).thenReturn("java.time.Instant");
    when(safeType.toString()).thenReturn("java.time.Instant");
    when(safeType.getActualTypeArguments()).thenReturn(List.of());

    CtExecutableReference<Object> execRef = mock(CtExecutableReference.class);
    doReturn(safeType).when(execRef).getDeclaringType();
    CtInvocation<Object> invocation = mock(CtInvocation.class);
    when(invocation.getExecutable()).thenReturn(execRef);
    when(invocation.getTarget()).thenReturn(null);

    CtFieldReference<Object> variable = mock(CtFieldReference.class);
    doReturn(safeType).when(variable).getDeclaringType();
    CtFieldAccess<Object> fieldAccess = mock(CtFieldAccess.class);
    when(fieldAccess.getVariable()).thenReturn(variable);
    when(fieldAccess.getTarget()).thenReturn(null);

    RemovedApiDetector.RemovedApiImportInfo info = new RemovedApiDetector.RemovedApiImportInfo();
    assertFalse(RemovedApiChecker.isRemovedApiElement(invocation, info));
    assertFalse(RemovedApiChecker.isRemovedApiElement(fieldAccess, info));
  }
}
