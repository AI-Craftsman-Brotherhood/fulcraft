package com.craftsmanbro.fulcraft.ui.cli.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.JavaParserAnalysisAdapter;
import com.craftsmanbro.fulcraft.plugins.analysis.adapter.parser.SpoonAnalysisAdapter;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

class DefaultServiceFactoryTest {

  private final DefaultServiceFactory factory = new DefaultServiceFactory(mock(Tracer.class));

  @Test
  void createAnalysisPort_trimsWhitespaceAroundEngineType() {
    assertThat(factory.createAnalysisPort(" spoon ")).isInstanceOf(SpoonAnalysisAdapter.class);
    assertThat(factory.createAnalysisPort("  JAVAparser  "))
        .isInstanceOf(JavaParserAnalysisAdapter.class);
  }

  @Test
  void createAnalysisPort_usesDefaultCompositeForBlankEngineType() {
    assertThat(factory.createAnalysisPort("   "))
        .isNotNull()
        .isNotInstanceOf(SpoonAnalysisAdapter.class)
        .isNotInstanceOf(JavaParserAnalysisAdapter.class);
  }

  @Test
  void createLlmClient_rejectsNullConfig() {
    assertThatThrownBy(() -> factory.createLlmClient(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("config must not be null");
  }

  @Test
  void constructor_rejectsNullTracer() {
    assertThatThrownBy(() -> new DefaultServiceFactory(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("tracer must not be null");
  }
}
