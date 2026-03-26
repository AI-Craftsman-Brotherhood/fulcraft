package com.craftsmanbro.fulcraft.infrastructure.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.infrastructure.system.contract.EnvironmentLookupPort;
import com.craftsmanbro.fulcraft.infrastructure.system.impl.Env;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EnvTest {

  @AfterEach
  void tearDown() {
    Env.reset();
  }

  @Test
  void get_returnsValueFromTestResolver() {
    Env.setForTest(
        name ->
            Map.of(
                    "API_KEY", "test-key",
                    "OTHER", "value")
                .get(name));

    String value = Env.get("API_KEY");

    assertThat(value).isEqualTo("test-key");
  }

  @Test
  void get_throwsException_whenNameIsNull() {
    assertThatThrownBy(() -> Env.get(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("Environment variable name must not be null");
  }

  @Test
  void getOrDefault_throwsException_whenNameIsNull() {
    assertThatThrownBy(() -> Env.getOrDefault(null, "fallback"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("Environment variable name must not be null");
  }

  @Test
  void getOrDefault_returnsDefault_whenValueIsNull() {
    Env.setForTest(name -> null);

    String value = Env.getOrDefault("MISSING", "fallback");

    assertThat(value).isEqualTo("fallback");
  }

  @Test
  void getOrDefault_returnsDefault_whenValueIsBlank() {
    Env.setForTest(name -> "  ");

    String value = Env.getOrDefault("BLANK", "fallback");

    assertThat(value).isEqualTo("fallback");
  }

  @Test
  void getOrDefault_returnsValue_whenValueIsNotBlank() {
    Env.setForTest(name -> "present");

    String value = Env.getOrDefault("KEY", "fallback");

    assertThat(value).isEqualTo("present");
  }

  @Test
  void setForTest_throwsException_whenResolverIsNull() {
    assertThatThrownBy(() -> Env.setForTest(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageEndingWith("Test resolver must not be null");
  }

  @Test
  void setForTest_replacesExistingResolver() {
    Env.setForTest(name -> "first");
    Env.setForTest(name -> "second");

    String value = Env.get("KEY");

    assertThat(value).isEqualTo("second");
  }

  @Test
  void reset_restoresSystemResolver() {
    Env.setForTest(name -> "override");
    Env.reset();

    String envValue = Env.get("PATH");
    String expected = System.getenv("PATH");

    assertThat(envValue).isEqualTo(expected);
  }

  @Test
  void port_exposesResolverContract() {
    EnvironmentLookupPort port = Env.port();
    Env.setForTest(name -> "KEY".equals(name) ? "value" : null);

    assertThat(port.resolve("KEY")).isEqualTo("value");
    assertThat(port.resolveOrDefault("MISSING", "fallback")).isEqualTo("fallback");
    assertThat(port.resolveVariable("MISSING").orDefault("fallback")).isEqualTo("fallback");
  }
}
