package com.craftsmanbro.fulcraft.ui.cli.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

class CliCommandTest {
  private static final String SERVICE_RESOURCE =
      "META-INF/services/com.craftsmanbro.fulcraft.ui.cli.spi.CliCommand";

  @Test
  void shouldExtendCallableWithIntegerReturnType() {
    Type[] interfaces = CliCommand.class.getGenericInterfaces();

    assertThat(interfaces).hasSize(1);
    assertThat(interfaces[0]).isInstanceOf(ParameterizedType.class);

    ParameterizedType callableType = (ParameterizedType) interfaces[0];
    assertThat(callableType.getRawType()).isEqualTo(java.util.concurrent.Callable.class);
    assertThat(callableType.getActualTypeArguments()).containsExactly(Integer.class);
  }

  @Test
  void shouldBeImplementableAsCallableCommand() throws Exception {
    CliCommand command = () -> 200;

    assertThat(command.call()).isEqualTo(200);
  }

  @Test
  void shouldBeMarkedAsFunctionalInterface() {
    assertThat(CliCommand.class).hasAnnotation(FunctionalInterface.class);
  }

  @Test
  void serviceRegistrationsShouldPointToConcretePicocliCommands() throws Exception {
    for (String providerClassName : loadServiceProviders()) {
      Class<?> providerClass = Class.forName(providerClassName);

      assertThat(CliCommand.class.isAssignableFrom(providerClass)).as(providerClassName).isTrue();
      assertThat(providerClass.isInterface()).as(providerClassName).isFalse();
      assertThat(Modifier.isAbstract(providerClass.getModifiers())).as(providerClassName).isFalse();
      assertThat(providerClass).as(providerClassName).hasAnnotation(Command.class);
    }
  }

  private static List<String> loadServiceProviders() throws Exception {
    InputStream stream = CliCommand.class.getClassLoader().getResourceAsStream(SERVICE_RESOURCE);

    assertThat(stream).as(SERVICE_RESOURCE).isNotNull();
    try (stream;
        var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty())
          .filter(line -> !line.startsWith("#"))
          .toList();
    }
  }
}
