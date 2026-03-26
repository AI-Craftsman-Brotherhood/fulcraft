package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import org.junit.jupiter.api.Test;

class MethodKeyUtilTest {

  @Test
  void methodKey_trimsInputs() {
    String key = MethodKeyUtil.methodKey(" com.example.Foo ", " bar() ", true);

    assertThat(key).isEqualTo("com.example.Foo#bar()");
  }

  @Test
  void methodKey_usesUnknownForBlankClassWhenConfigured() {
    String key = MethodKeyUtil.methodKey("  ", "foo()", true);

    assertThat(key).isEqualTo(MethodInfo.UNKNOWN + "#foo()");
  }

  @Test
  void methodKey_allowsEmptyClassWhenTreatBlankFalse() {
    String key = MethodKeyUtil.methodKey(" ", "foo()", false);

    assertThat(key).isEqualTo("#foo()");
  }

  @Test
  void methodKey_usesUnknownForNullClassWhenTreatBlankFalse() {
    String key = MethodKeyUtil.methodKey(null, "foo()", false);

    assertThat(key).isEqualTo(MethodInfo.UNKNOWN + "#foo()");
  }

  @Test
  void methodKey_handlesNullSignature() {
    String key = MethodKeyUtil.methodKey("com.example.Foo", null, true);

    assertThat(key).isEqualTo("com.example.Foo#");
  }
}
