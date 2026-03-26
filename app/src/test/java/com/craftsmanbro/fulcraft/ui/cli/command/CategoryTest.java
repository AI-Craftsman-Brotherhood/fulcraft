package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class CategoryTest {

  @Test
  void hasRuntimeRetention() {
    Retention retention = Category.class.getAnnotation(Retention.class);

    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void targetsTypeDeclarations() {
    Target target = Category.class.getAnnotation(Target.class);

    assertThat(target).isNotNull();
    assertThat(target.value()).containsExactly(ElementType.TYPE);
  }

  @Test
  void declaresValueAttribute() throws NoSuchMethodException {
    Method valueMethod = Category.class.getDeclaredMethod("value");

    assertThat(valueMethod.getReturnType()).isEqualTo(String.class);
  }
}
