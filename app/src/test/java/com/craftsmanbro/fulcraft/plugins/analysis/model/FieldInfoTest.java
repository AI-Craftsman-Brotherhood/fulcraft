package com.craftsmanbro.fulcraft.plugins.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FieldInfoTest {

  @Test
  void setters_updateAllFields() {
    FieldInfo info = new FieldInfo();

    info.setName("count");
    info.setType("int");
    info.setVisibility("private");
    info.setStatic(true);
    info.setFinal(true);
    info.setInjectable(true);
    info.setMockHint("required");

    assertThat(info.getName()).isEqualTo("count");
    assertThat(info.getType()).isEqualTo("int");
    assertThat(info.getVisibility()).isEqualTo("private");
    assertThat(info.isStatic()).isTrue();
    assertThat(info.isFinal()).isTrue();
    assertThat(info.isInjectable()).isTrue();
    assertThat(info.getMockHint()).isEqualTo("required");
  }
}
