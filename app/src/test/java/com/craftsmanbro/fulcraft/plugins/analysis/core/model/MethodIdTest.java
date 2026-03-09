package com.craftsmanbro.fulcraft.plugins.analysis.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MethodIdTest {

  @Test
  void constructor_trimsInputsAndNormalizesReturnType() {
    MethodId id = new MethodId("  com.acme.Foo  ", "  bar  ", List.of("int"), "  void  ");

    assertThat(id.declaringClassFqn()).isEqualTo("com.acme.Foo");
    assertThat(id.methodName()).isEqualTo("bar");
    assertThat(id.parameterTypes()).containsExactly("int");
    assertThat(id.returnType()).isEqualTo("void");
  }

  @Test
  void constructor_usesEmptyValues_whenNullsProvided() {
    MethodId id = new MethodId(null, null, null, "   ");

    assertThat(id.declaringClassFqn()).isEmpty();
    assertThat(id.methodName()).isEmpty();
    assertThat(id.parameterTypes()).isEmpty();
    assertThat(id.returnType()).isNull();
  }

  @Test
  void parameterTypes_areCopiedAndUnmodifiable() {
    List<String> params = new ArrayList<>(List.of("int"));
    MethodId id = new MethodId("C", "m", params, null);

    params.add("java.lang.String");

    assertThat(id.parameterTypes()).containsExactly("int");
    assertThatThrownBy(() -> id.parameterTypes().add("long"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void toString_omitsReturnTypeWhenNull() {
    MethodId id = new MethodId("C", "m", List.of("int", "java.lang.String"), null);

    assertThat(id.toString()).isEqualTo("C#m(int,java.lang.String)");
  }

  @Test
  void toString_includesReturnTypeWhenPresent() {
    MethodId id = new MethodId("C", "m", List.of(), "void");

    assertThat(id.toString()).isEqualTo("C#m():void");
  }

  @Test
  void equalsAndHashCode_considerNormalizedReturnType() {
    MethodId id1 = new MethodId("C", "m", List.of("int"), null);
    MethodId id2 = new MethodId("C", "m", List.of("int"), "  ");
    MethodId id3 = new MethodId("C", "m", List.of("int"), "void");

    assertThat(id1).isEqualTo(id2);
    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    assertThat(id1).isNotEqualTo(id3);
  }
}
