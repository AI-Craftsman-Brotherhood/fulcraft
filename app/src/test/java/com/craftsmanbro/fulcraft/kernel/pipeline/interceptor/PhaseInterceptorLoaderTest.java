package com.craftsmanbro.fulcraft.kernel.pipeline.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Unit tests for {@link PhaseInterceptorLoader}. */
@ExtendWith(KernelPortTestExtension.class)
class PhaseInterceptorLoaderTest {

  private PhaseInterceptorLoader loader;

  @BeforeEach
  void setUp() {
    loader = new PhaseInterceptorLoader();
  }

  @Test
  void shouldRequireNonNullConfigWhenLoadingAll() {
    assertThatThrownBy(() -> loader.loadAll(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.interceptor.loader.error.config_null"));
  }

  @Test
  void shouldInitializeAllStepAndHookBuckets() {
    Map<String, Map<Hook, List<PhaseInterceptor>>> result = loader.loadAll(new Config());

    assertThat(result).containsKeys(PipelineNodeIds.ANALYZE, PipelineNodeIds.GENERATE);
    result.values().forEach(hookMap -> assertThat(hookMap).containsKeys(Hook.PRE, Hook.POST));
  }

  @Test
  void shouldReturnImmutableCollections() {
    Map<String, Map<Hook, List<PhaseInterceptor>>> result = loader.loadAll(new Config());

    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.get(PipelineNodeIds.ANALYZE).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.get(PipelineNodeIds.ANALYZE).get(Hook.PRE).add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldLoadBuiltInAnalyzePreInterceptor() {
    List<String> interceptorIds =
        loader.loadFor(new Config(), PipelineNodeIds.ANALYZE, Hook.PRE).stream()
            .map(PhaseInterceptor::id)
            .toList();

    assertThat(interceptorIds).contains("config-loader");
  }

  @Test
  void shouldRespectEnabledFalseFromConfiguration() {
    Config.InterceptorsConfig interceptorsConfig = new Config.InterceptorsConfig();
    Config.PhaseInterceptorsConfig analyzeConfig = new Config.PhaseInterceptorsConfig();
    analyzeConfig.setPre(List.of(interceptorEntry(ConfigLoaderInterceptor.class, false, null)));
    interceptorsConfig.setForStep("ANALYZE", analyzeConfig);

    Config config = new Config();
    config.setInterceptors(interceptorsConfig);

    List<String> interceptorIds =
        loader.loadFor(config, PipelineNodeIds.ANALYZE, Hook.PRE).stream()
            .map(PhaseInterceptor::id)
            .toList();

    assertThat(interceptorIds).doesNotContain("config-loader");
  }

  @Test
  void shouldApplyOrderOverridesFromConfiguration() {
    Config.InterceptorsConfig interceptorsConfig = new Config.InterceptorsConfig();
    Config.PhaseInterceptorsConfig generateConfig = new Config.PhaseInterceptorsConfig();
    generateConfig.setPre(
        List.of(
            interceptorEntry(
                com.craftsmanbro.fulcraft.plugins.analysis.interceptor.SecurityFilterInterceptor
                    .class,
                true,
                5)));
    interceptorsConfig.setForStep("GENERATE", generateConfig);

    Config config = new Config();
    config.setInterceptors(interceptorsConfig);

    List<String> interceptorIds =
        loader.loadFor(config, PipelineNodeIds.GENERATE, Hook.PRE).stream()
            .map(PhaseInterceptor::id)
            .toList();

    assertThat(interceptorIds).containsExactly("security-filter");
  }

  @Test
  void shouldRequireNonNullPhaseInLoadFor() {
    assertThatThrownBy(() -> loader.loadFor(new Config(), null, Hook.PRE))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.interceptor.loader.error.phase_null"));
  }

  @Test
  void shouldRequireNonNullHookInLoadFor() {
    assertThatThrownBy(() -> loader.loadFor(new Config(), PipelineNodeIds.ANALYZE, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(MessageSource.getMessage("kernel.interceptor.loader.error.hook_null"));
  }

  private Config.InterceptorEntryConfig interceptorEntry(
      Class<? extends PhaseInterceptor> interceptorClass, Boolean enabled, Integer order) {
    Config.InterceptorEntryConfig entry = new Config.InterceptorEntryConfig();
    entry.setClassName(interceptorClass.getName());
    entry.setEnabled(enabled);
    entry.setOrder(order);
    return entry;
  }
}
