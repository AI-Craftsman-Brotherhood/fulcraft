package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassInfoTest {

  @Test
  void methodCount_fallsBackToStoredValueWhenNoMethods() {
    ClassInfo info = new ClassInfo();
    info.setMethodCount(3);

    assertThat(info.getMethodCount()).isEqualTo(3);
  }

  @Test
  void methodCount_tracksMethodsWhenPresent() {
    ClassInfo info = new ClassInfo();

    info.addMethod(new MethodInfo());
    info.addMethod(new MethodInfo());

    assertThat(info.getMethodCount()).isEqualTo(2);

    info.setMethodCount(10);
    assertThat(info.getMethodCount()).isEqualTo(2);
  }

  @Test
  void setMethods_updatesMethodCount() {
    ClassInfo info = new ClassInfo();

    List<MethodInfo> methods = new ArrayList<>();
    methods.add(new MethodInfo());
    methods.add(new MethodInfo());

    info.setMethods(methods);

    assertThat(info.getMethodCount()).isEqualTo(2);
  }

  @Test
  void setMethods_nullResetsMethodCountToZero() {
    ClassInfo info = new ClassInfo();
    info.addMethod(new MethodInfo());

    info.setMethods(null);

    assertThat(info.getMethodCount()).isZero();
    assertThat(info.getMethods()).isEmpty();
  }

  @Test
  void adders_ignoreNullOrBlankValues() {
    ClassInfo info = new ClassInfo();

    info.addExtendsType("java.lang.Object");
    info.addExtendsType(" ");
    info.addExtendsType(null);

    info.addImplementsType("java.io.Serializable");
    info.addImplementsType("");
    info.addImplementsType(null);

    info.addAnnotation("Deprecated");
    info.addAnnotation("  ");
    info.addAnnotation(null);

    info.addImport("java.util.List");
    info.addImport("");
    info.addImport(null);

    assertThat(info.getExtendsTypes()).containsExactly("java.lang.Object");
    assertThat(info.getImplementsTypes()).containsExactly("java.io.Serializable");
    assertThat(info.getAnnotations()).containsExactly("Deprecated");
    assertThat(info.getImports()).containsExactly("java.util.List");
  }

  @Test
  void getters_returnUnmodifiableLists() {
    ClassInfo info = new ClassInfo();
    info.addMethod(new MethodInfo());
    info.addField(new FieldInfo());

    assertThatThrownBy(() -> info.getMethods().add(new MethodInfo()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> info.getFields().add(new FieldInfo()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void addMethod_ignoresNull() {
    ClassInfo info = new ClassInfo();

    info.addMethod(null);

    assertThat(info.getMethods()).isEmpty();
    assertThat(info.getMethodCount()).isZero();
  }

  @Test
  void listSetters_acceptNullAndExposeEmptyLists() {
    ClassInfo info = new ClassInfo();

    info.setExtendsTypes(null);
    info.setImplementsTypes(null);
    info.setFields(null);
    info.setAnnotations(null);
    info.setImports(null);

    assertThat(info.getExtendsTypes()).isEmpty();
    assertThat(info.getImplementsTypes()).isEmpty();
    assertThat(info.getFields()).isEmpty();
    assertThat(info.getAnnotations()).isEmpty();
    assertThat(info.getImports()).isEmpty();
  }
}
