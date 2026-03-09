package com.craftsmanbro.fulcraft.infrastructure.parser.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RemovedApiUtilsTest {

  @Test
  void matchesTypeName_detectsRemovedApiInsideGenerics() {
    RemovedApiDetector.RemovedApiImportInfo info = new RemovedApiDetector.RemovedApiImportInfo();

    assertTrue(RemovedApiDetector.matchesTypeName("List<javax.xml.bind.JAXBElement>", info));
    assertTrue(
        RemovedApiDetector.matchesTypeName(
            "Map<String, ? extends javax.xml.bind.JAXBElement[]>", info));
  }

  @Test
  void fromImports_doesNotAddSimpleNamesForWildcardImports() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.annotation.*"));

    assertTrue(info.getWildcardPackages().contains("javax.xml.bind"));
    assertFalse(info.getImportedSimpleNames().contains("annotation"));
    assertFalse(RemovedApiDetector.matchesTypeName("annotation", info));
  }

  @Test
  void matchesTypeName_detectsNestedTypeFromImportedSimpleName() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.JAXBElement"));

    assertTrue(RemovedApiDetector.matchesTypeName("JAXBElement.Inner", info));
  }

  @Test
  void fromImports_skipsSimpleNamesForStaticMembers() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("static javax.xml.bind.JAXBElement.valueOf"));

    assertTrue(info.getImportedQualifiedNames().contains("javax.xml.bind.JAXBElement.valueOf"));
    assertFalse(info.getImportedSimpleNames().contains("valueOf"));
    assertFalse(RemovedApiDetector.matchesTypeName("valueOf", info));
  }

  @Test
  void fromImports_returnsEmptyInfoForNullAndIrrelevantInputs() {
    RemovedApiDetector.RemovedApiImportInfo nullInfo = RemovedApiDetector.fromImports(null);
    assertTrue(nullInfo.getImportedQualifiedNames().isEmpty());
    assertTrue(nullInfo.getImportedSimpleNames().isEmpty());
    assertTrue(nullInfo.getWildcardPackages().isEmpty());

    RemovedApiDetector.RemovedApiImportInfo irrelevantInfo =
        RemovedApiDetector.fromImports(
            Arrays.asList(
                null, "", "  ", "java.util.List", "static java.util.Collections.emptyList"));
    assertTrue(irrelevantInfo.getImportedQualifiedNames().isEmpty());
    assertTrue(irrelevantInfo.getImportedSimpleNames().isEmpty());
    assertTrue(irrelevantInfo.getWildcardPackages().isEmpty());
  }

  @Test
  void fromImports_normalizesSemicolonAndCollectsSimpleAndQualifiedNames() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(
            List.of(" javax.xml.bind.JAXBElement; ", "javax.xml.ws.Service;"));

    assertTrue(info.getImportedQualifiedNames().contains("javax.xml.bind.JAXBElement"));
    assertTrue(info.getImportedQualifiedNames().contains("javax.xml.ws.Service"));
    assertTrue(info.getImportedSimpleNames().contains("JAXBElement"));
    assertTrue(info.getImportedSimpleNames().contains("Service"));
  }

  @Test
  void matchesQualifiedName_usesWildcardPrefixFromImportInfo() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.*"));

    assertTrue(RemovedApiDetector.matchesQualifiedName("javax.xml.bind.api.Client", info));
  }

  @Test
  void matchesTypeName_usesWildcardPrefixFromImportInfo() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.*"));

    assertTrue(RemovedApiDetector.matchesTypeName("javax.xml.bind.api.Client", info));
  }

  @Test
  void matchesPackageName_usesWildcardPrefixFromImportInfo() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.*"));

    assertTrue(RemovedApiDetector.matchesPackageName("javax.xml.bind.api", info));
    assertFalse(RemovedApiDetector.matchesPackageName("modern.api", info));
  }

  @Test
  void matchesQualifiedName_detectsRemovedApiInsideGenericArguments() {
    assertTrue(
        RemovedApiDetector.matchesQualifiedName(
            "java.util.Map<java.lang.String, javax.xml.ws.Service>", null));
  }

  @Test
  void normalizeTypeName_handlesWildcardsArraysVarargsAndJavaLangPrefix() {
    assertEquals("String", RemovedApiDetector.normalizeTypeName("? extends java.lang.String[][]"));
    assertEquals(
        "javax.xml.bind.JAXBElement",
        RemovedApiDetector.normalizeTypeName("? super javax.xml.bind.JAXBElement..."));
    assertEquals("java.util.List", RemovedApiDetector.normalizeTypeName("java.util.List<String>"));
  }

  @Test
  void removedApiImportInfo_gettersReturnDefensiveUnmodifiableCopies() {
    RemovedApiDetector.RemovedApiImportInfo info =
        RemovedApiDetector.fromImports(List.of("javax.xml.bind.JAXBElement", "javax.xml.bind.*"));

    Set<String> simpleNames = info.getImportedSimpleNames();
    Set<String> qualifiedNames = info.getImportedQualifiedNames();
    Set<String> wildcardPackages = info.getWildcardPackages();

    assertThrows(UnsupportedOperationException.class, () -> simpleNames.add("Other"));
    assertThrows(UnsupportedOperationException.class, () -> qualifiedNames.add("Other"));
    assertThrows(UnsupportedOperationException.class, () -> wildcardPackages.add("legacy.removed"));
  }

  @Test
  void constructor_throwsIllegalStateException() throws Exception {
    Constructor<RemovedApiDetector> constructor = RemovedApiDetector.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    InvocationTargetException exception =
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    assertTrue(exception.getCause() instanceof IllegalStateException);
  }
}
