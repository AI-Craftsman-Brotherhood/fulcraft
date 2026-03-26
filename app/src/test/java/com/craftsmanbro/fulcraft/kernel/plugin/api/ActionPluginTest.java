package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class ActionPluginTest {

  @Test
  void shouldImplementPluginInterface() {
    ActionPlugin plugin =
        new ActionPlugin() {
          @Override
          public String id() {
            return "test-plugin";
          }

          @Override
          public String kind() {
            return "analyze";
          }

          @Override
          public PluginResult execute(PluginContext context) {
            return PluginResult.success("test-plugin", "executed");
          }
        };

    assertThat(plugin.id()).isEqualTo("test-plugin");
    assertThat(plugin.kind()).isEqualTo("analyze");
  }

  @Test
  void shouldExecuteAndReturnResult() throws Exception {
    PluginContext context = mock(PluginContext.class);
    ActionPlugin plugin =
        new ActionPlugin() {
          @Override
          public String id() {
            return "execute-test";
          }

          @Override
          public String kind() {
            return "generate";
          }

          @Override
          public PluginResult execute(PluginContext ctx) {
            return PluginResult.success(id(), "completed");
          }
        };

    PluginResult result = plugin.execute(context);

    assertThat(result).isNotNull();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getPluginId()).isEqualTo("execute-test");
  }

  @Test
  void shouldAllowExceptionThrowingExecution() {
    ActionPlugin plugin =
        new ActionPlugin() {
          @Override
          public String id() {
            return "throwing-plugin";
          }

          @Override
          public String kind() {
            return "run";
          }

          @Override
          public PluginResult execute(PluginContext context) throws ActionPluginException {
            throw new ActionPluginException("expected failure");
          }
        };

    assertThatThrownBy(() -> plugin.execute(mock(PluginContext.class)))
        .isInstanceOf(ActionPluginException.class)
        .hasMessage("expected failure");
  }
}
