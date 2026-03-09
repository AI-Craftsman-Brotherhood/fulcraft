package com.craftsmanbro.fulcraft.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {

  @Test
  void mutableSectionGetters_reuseTheSameInstance() {
    Config config = new Config();

    assertThat(config.getVerification()).isSameAs(config.getVerification());
    assertThat(config.getContextAwareness()).isSameAs(config.getContextAwareness());
    assertThat(config.getGeneration()).isSameAs(config.getGeneration());
    assertThat(config.getGovernance()).isSameAs(config.getGovernance());
    assertThat(config.getAudit()).isSameAs(config.getAudit());
    assertThat(config.getQuota()).isSameAs(config.getQuota());
    assertThat(config.getMocking()).isSameAs(config.getMocking());
    assertThat(config.getLocalFix()).isSameAs(config.getLocalFix());
    assertThat(config.getLog()).isSameAs(config.getLog());
    assertThat(config.getQualityGate()).isSameAs(config.getQualityGate());
  }

  @Test
  void qualityGateMutation_persistsAcrossGetterCalls() {
    Config config = new Config();

    config.getQualityGate().setCoverageTool("jacoco");

    assertThat(config.getQualityGate().getCoverageTool()).isEqualTo("jacoco");
  }

  @Test
  void governanceRedactionGetter_reusesTheSameInstance() {
    Config.GovernanceConfig governance = new Config.GovernanceConfig();
    Config.GovernanceConfig.RedactionConfig redaction = governance.getRedaction();

    redaction.setMode("report");

    assertThat(governance.getRedaction()).isSameAs(redaction);
    assertThat(governance.getRedaction().getMode()).isEqualTo("report");
  }

  @Test
  void interceptorsConfig_resolvesDocumentStepCaseInsensitively() {
    Config.InterceptorsConfig interceptors = new Config.InterceptorsConfig();
    Config.PhaseInterceptorsConfig documentConfig = new Config.PhaseInterceptorsConfig();

    interceptors.setForStep("DOCUMENT", documentConfig);

    assertThat(interceptors.getForStep("DOCUMENT")).isSameAs(documentConfig);
    assertThat(interceptors.getForStep("document")).isSameAs(documentConfig);
  }

  @Test
  void interceptorsConfig_resolvesArbitraryPluginStep() {
    Config.InterceptorsConfig interceptors = new Config.InterceptorsConfig();
    Config.PhaseInterceptorsConfig pluginPhaseConfig = new Config.PhaseInterceptorsConfig();

    interceptors.setForStep("custom_plugin_phase", pluginPhaseConfig);

    assertThat(interceptors.getForStep("CUSTOM_PLUGIN_PHASE")).isSameAs(pluginPhaseConfig);
  }

  @Test
  void phaseInterceptorsConfig_reinitializesNullListsAsMutable() {
    Config.PhaseInterceptorsConfig phaseConfig = new Config.PhaseInterceptorsConfig();
    phaseConfig.setPre(null);
    phaseConfig.setPost(null);
    Config.InterceptorEntryConfig entry = new Config.InterceptorEntryConfig();
    entry.setClassName("com.example.DummyInterceptor");

    phaseConfig.getPre().add(entry);
    phaseConfig.getPost().add(entry);

    assertThat(phaseConfig.getPre()).hasSize(1);
    assertThat(phaseConfig.getPost()).hasSize(1);
  }

  @Test
  void pipelineConfig_returnsEmptyStagesWhenUnspecified() {
    Config.PipelineConfig pipelineConfig = new Config.PipelineConfig();

    assertThat(pipelineConfig.getStages()).isEmpty();
  }

  @Test
  void pipelineConfig_normalizesAndSkipsBlankStages() {
    Config.PipelineConfig pipelineConfig = new Config.PipelineConfig();
    pipelineConfig.setStages(List.of(" ANALYZE ", "  ", "REPORT"));

    assertThat(pipelineConfig.getStages()).containsExactly("analyze", "report");
  }
}
