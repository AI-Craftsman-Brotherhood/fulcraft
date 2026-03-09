package com.craftsmanbro.fulcraft.kernel.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginContextTest {

  @Test
  void shouldStoreAndReturnRunContext() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);

    PluginContext context = new PluginContext(runContext, artifactStore);

    assertThat(context.getRunContext()).isSameAs(runContext);
  }

  @Test
  void shouldStoreAndReturnArtifactStore() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);

    PluginContext context = new PluginContext(runContext, artifactStore);

    assertThat(context.getArtifactStore()).isSameAs(artifactStore);
  }

  @Test
  void shouldUseEmptyPluginConfigWhenNotProvided() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);

    PluginContext context = new PluginContext(runContext, artifactStore);

    assertThat(context.getPluginConfig()).isNotNull();
    assertThat(context.getPluginConfig().configExists()).isFalse();
    assertThat(context.getPluginConfig().getPluginId()).isEmpty();
    assertThat(context.getServiceRegistry()).isNotNull();
  }

  @Test
  void shouldStoreAndReturnProvidedPluginConfig() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);
    PluginConfig pluginConfig = mock(PluginConfig.class);

    PluginContext context = new PluginContext(runContext, artifactStore, pluginConfig);

    assertThat(context.getPluginConfig()).isSameAs(pluginConfig);
  }

  @Test
  void shouldStoreAndReturnProvidedServiceRegistry() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);
    PluginConfig pluginConfig = mock(PluginConfig.class);
    ServiceRegistry serviceRegistry = new ServiceRegistry();

    PluginContext context =
        new PluginContext(
            runContext, artifactStore, pluginConfig, "node-1", Map.of(), serviceRegistry);

    assertThat(context.getServiceRegistry()).isSameAs(serviceRegistry);
  }

  @Test
  void shouldThrowNullPointerExceptionWhenServiceRegistryIsNull() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);
    PluginConfig pluginConfig = mock(PluginConfig.class);

    assertThatThrownBy(
            () ->
                new PluginContext(
                    runContext, artifactStore, pluginConfig, "node-1", Map.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(
            MessageSource.getMessage("kernel.common.error.argument_null", "serviceRegistry"));
  }

  @Test
  void shouldThrowNullPointerExceptionWhenRunContextIsNull() {
    ArtifactStore artifactStore = mock(ArtifactStore.class);

    assertThatThrownBy(() -> new PluginContext(null, artifactStore))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "runContext"));
  }

  @Test
  void shouldThrowNullPointerExceptionWhenArtifactStoreIsNull() {
    RunContext runContext = mock(RunContext.class);

    assertThatThrownBy(() -> new PluginContext(runContext, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "artifactStore"));
  }

  @Test
  void shouldThrowNullPointerExceptionWhenPluginConfigIsNull() {
    RunContext runContext = mock(RunContext.class);
    ArtifactStore artifactStore = mock(ArtifactStore.class);

    assertThatThrownBy(() -> new PluginContext(runContext, artifactStore, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.common.error.argument_null", "pluginConfig"));
  }
}
