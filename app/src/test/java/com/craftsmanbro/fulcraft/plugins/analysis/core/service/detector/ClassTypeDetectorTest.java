package com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector;

import static org.junit.jupiter.api.Assertions.*;

import com.craftsmanbro.fulcraft.plugins.analysis.core.service.detector.ClassTypeDetector.ClassType;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.FieldInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClassTypeDetectorTest {

  private ClassTypeDetector detector;

  @BeforeEach
  void setUp() {
    detector = new ClassTypeDetector();
  }

  @Test
  void detectClassTypeShouldReturnGeneralForNull() {
    // act
    ClassType result = detector.detectClassType(null);

    // assert
    assertEquals(ClassType.GENERAL, result);
  }

  @Test
  void detectClassTypeShouldReturnServiceForServiceAnnotation() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setAnnotations(List.of("Service"));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.SERVICE, result);
  }

  @Test
  void detectClassTypeShouldNormalizeQualifiedAnnotationName() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setAnnotations(List.of("@org.springframework.stereotype.Service"));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.SERVICE, result);
  }

  @ParameterizedTest
  @CsvSource({
    "Service, SERVICE",
    "Component, SERVICE",
    "Controller, SERVICE",
    "RestController, SERVICE",
    "Repository, SERVICE"
  })
  void detectClassTypeShouldReturnServiceForSpringAnnotations(
      String annotation, String expectedType) {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setAnnotations(List.of(annotation));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.valueOf(expectedType), result);
  }

  @ParameterizedTest
  @CsvSource({"Entity, DATA_CLASS", "Data, DATA_CLASS", "Value, DATA_CLASS"})
  void detectClassTypeShouldReturnDataClassForDataAnnotations(
      String annotation, String expectedType) {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setAnnotations(List.of(annotation));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.valueOf(expectedType), result);
  }

  @Test
  void detectClassTypeShouldReturnExceptionForExceptionSuperclass() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setExtendsTypes(List.of("RuntimeException"));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.EXCEPTION, result);
  }

  @Test
  void detectClassTypeShouldReturnUtilityForStaticMethodsAndPrivateConstructor() {
    // arrange
    ClassInfo cls = new ClassInfo();

    MethodInfo privateConstructor = new MethodInfo();
    privateConstructor.setName("<init>");
    privateConstructor.setVisibility("private");

    MethodInfo staticMethod = new MethodInfo();
    staticMethod.setName("format");
    staticMethod.setSignature("public static String format(String s)");
    staticMethod.setStatic(true);

    cls.setMethods(List.of(privateConstructor, staticMethod));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.UTILITY, result);
  }

  @Test
  void detectClassTypeShouldReturnUtilityForTwoStaticMethodsWithoutInstanceMethods() {
    // arrange
    ClassInfo cls = new ClassInfo();

    MethodInfo firstStaticMethod = new MethodInfo();
    firstStaticMethod.setName("format");
    firstStaticMethod.setStatic(true);

    MethodInfo secondStaticMethod = new MethodInfo();
    secondStaticMethod.setName("parse");
    secondStaticMethod.setStatic(true);

    cls.setMethods(List.of(firstStaticMethod, secondStaticMethod));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.UTILITY, result);
  }

  @Test
  void detectClassTypeShouldReturnBuilderForBuildMethod() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.PersonBuilder");

    MethodInfo buildMethod = new MethodInfo();
    buildMethod.setName("build");
    buildMethod.setSignature("public Person build()");

    cls.setMethods(List.of(buildMethod));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.BUILDER, result);
  }

  @Test
  void detectClassTypeShouldReturnBuilderForFluentSetters() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.PersonBuilder");

    MethodInfo setName = new MethodInfo();
    setName.setName("setName");
    setName.setSignature("PersonBuilder setName(String)");

    MethodInfo withAge = new MethodInfo();
    withAge.setName("withAge");
    withAge.setSignature("PersonBuilder withAge(int)");

    cls.setMethods(List.of(setName, withAge));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.BUILDER, result);
  }

  @Test
  void detectClassTypeShouldReturnDataClassForGettersSettersAndFields() {
    // arrange
    ClassInfo cls = new ClassInfo();

    FieldInfo field1 = new FieldInfo();
    field1.setName("firstName");
    FieldInfo field2 = new FieldInfo();
    field2.setName("lastName");

    MethodInfo getter1 = new MethodInfo();
    getter1.setName("getFirstName");
    MethodInfo getter2 = new MethodInfo();
    getter2.setName("getLastName");
    MethodInfo setter1 = new MethodInfo();
    setter1.setName("setFirstName");
    MethodInfo equals = new MethodInfo();
    equals.setName("equals");

    cls.setFields(List.of(field1, field2));
    cls.setMethods(List.of(getter1, getter2, setter1, equals));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.DATA_CLASS, result);
  }

  @Test
  void detectClassTypeShouldReturnServiceForDataClassWithActionMethod() {
    // arrange
    ClassInfo cls = new ClassInfo();
    cls.setFqn("com.example.CustomerProfile");

    FieldInfo field1 = new FieldInfo();
    field1.setName("id");
    FieldInfo field2 = new FieldInfo();
    field2.setName("name");

    MethodInfo getter1 = new MethodInfo();
    getter1.setName("getId");
    MethodInfo getter2 = new MethodInfo();
    getter2.setName("getName");
    MethodInfo action = new MethodInfo();
    action.setName("refreshFromRemote");

    cls.setFields(List.of(field1, field2));
    cls.setMethods(List.of(getter1, getter2, action));

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.SERVICE, result);
  }

  @Test
  void detectClassTypeShouldReturnGeneralForEmptyClass() {
    // arrange
    ClassInfo cls = new ClassInfo();

    // act
    ClassType result = detector.detectClassType(cls);

    // assert
    assertEquals(ClassType.GENERAL, result);
  }
}
