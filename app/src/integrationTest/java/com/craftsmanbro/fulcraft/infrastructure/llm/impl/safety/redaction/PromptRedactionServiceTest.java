package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.config.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptRedactionServiceTest {

  @Test
  void legacyMode_redactsWithoutBlocking() {
    PromptRedactionService service = new PromptRedactionService();

    RedactionResult result = service.redactPrompt("email support@craftsmann-bro.com");

    assertThat(service.isDetectorChainMode()).isFalse();
    assertThat(service.getEnabledDetectors()).containsExactly("legacy-regex");
    assertThat(result.redactedText()).contains(PromptRedactionService.DEFAULT_MASK);
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  @Test
  void reportMode_returnsOriginalTextAndFindings() {
    PromptRedactionService service =
        new PromptRedactionService(config("report", 0.6, 0.99), Path.of("."));

    RedactionResult result = service.redactPrompt("email support@craftsmann-bro.com");

    assertThat(result.redactedText()).isEqualTo("email support@craftsmann-bro.com");
    assertThat(result.report().emailCount()).isEqualTo(1);
    assertThat(result.findings()).isNotEmpty();
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  @Test
  void enforceMode_masksWhenThresholdExceeded() {
    PromptRedactionService service =
        new PromptRedactionService(config("enforce", 0.6, 0.99), Path.of("."));

    RedactionResult result = service.redactPrompt("email support@craftsmann-bro.com");

    assertThat(result.redactedText()).contains(PromptRedactionService.DEFAULT_MASK);
    assertThat(result.report().emailCount()).isEqualTo(1);
    assertThat(RedactionContext.consumePrompt()).isEqualTo("email support@craftsmann-bro.com");
    assertThat(RedactionContext.consumeReport()).isEqualTo(result.report());
  }

  @Test
  void redactPromptWithoutContext_doesNotStoreContext() {
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();

    PromptRedactionService service =
        new PromptRedactionService(config("report", 0.6, 0.99), Path.of("."));

    service.redactPromptWithoutContext("email support@craftsmann-bro.com");

    assertThat(RedactionContext.consumePrompt()).isNull();
    assertThat(RedactionContext.consumeReport()).isNull();
  }

  @Test
  void redactPromptForStorage_forcesMaskEvenInReportMode() {
    PromptRedactionService service =
        new PromptRedactionService(config("report", 0.6, 0.99), Path.of("."));

    RedactionResult result = service.redactPromptForStorage("email support@craftsmann-bro.com");

    assertThat(result.redactedText()).contains(PromptRedactionService.DEFAULT_MASK);
  }

  @Test
  void enforceMode_blocksWhenAboveThreshold() {
    PromptRedactionService service =
        new PromptRedactionService(config("enforce", 0.6, 0.5), Path.of("."));

    assertThatThrownBy(() -> service.redactPrompt("email support@craftsmann-bro.com"))
        .isInstanceOf(RedactionException.class)
        .hasMessageContaining("Prompt redaction failed; aborting LLM request.")
        .satisfies(ex -> assertThat(ex.getCause()).hasMessageContaining("Sensitive data blocked"));
  }

  @Test
  void fromConfig_returnsLegacyModeWhenConfigIsNullOrOff() {
    PromptRedactionService nullConfigService =
        PromptRedactionService.fromConfig(null, Path.of("."));
    assertThat(nullConfigService.isDetectorChainMode()).isFalse();

    Config config = new Config();
    Config.GovernanceConfig governance = new Config.GovernanceConfig();
    Config.GovernanceConfig.RedactionConfig redaction =
        new Config.GovernanceConfig.RedactionConfig();
    redaction.setMode("off");
    governance.setRedaction(redaction);
    config.setGovernance(governance);

    PromptRedactionService offModeService = PromptRedactionService.fromConfig(config, Path.of("."));
    assertThat(offModeService.isDetectorChainMode()).isFalse();
  }

  @Test
  void detectorChainMode_off_returnsUnchangedText() {
    PromptRedactionService service =
        new PromptRedactionService(config("off", 0.6, 0.99), Path.of("."));

    RedactionResult result = service.redactPrompt("email support@craftsmann-bro.com");

    assertThat(service.isDetectorChainMode()).isTrue();
    assertThat(result.redactedText()).isEqualTo("email support@craftsmann-bro.com");
    assertThat(result.report()).isEqualTo(RedactionReport.EMPTY);
    assertThat(result.findings()).isEmpty();
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  @Test
  void enforceMode_withoutMaskThresholdHit_returnsOriginalTextWithFindings() {
    PromptRedactionService service =
        new PromptRedactionService(config("enforce", 0.95, 0.99), Path.of("."));

    RedactionResult result = service.redactPrompt("email support@craftsmann-bro.com");

    assertThat(result.redactedText()).isEqualTo("email support@craftsmann-bro.com");
    assertThat(result.report().emailCount()).isEqualTo(1);
    assertThat(result.findings()).hasSize(1);
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  @Test
  void dictionaryAllowlist_canSuppressDictionaryFindings(@TempDir Path tempDir) throws IOException {
    Files.writeString(tempDir.resolve("deny.txt"), "secret\n");
    Files.writeString(tempDir.resolve("allow.txt"), "secret\n");

    Config.GovernanceConfig.RedactionConfig config = new Config.GovernanceConfig.RedactionConfig();
    config.setMode("enforce");
    config.setDetectors(List.of("dictionary"));
    config.setMaskThreshold(0.6);
    config.setBlockThreshold(0.99);
    config.setDenylistPath("deny.txt");
    config.setAllowlistPath("allow.txt");

    PromptRedactionService service = new PromptRedactionService(config, tempDir);

    RedactionResult result = service.redactPrompt("secret");

    assertThat(service.getEnabledDetectors()).containsExactly("dictionary");
    assertThat(result.redactedText()).isEqualTo("secret");
    assertThat(result.report()).isEqualTo(RedactionReport.EMPTY);
    assertThat(result.findings()).isEmpty();
    RedactionContext.consumePrompt();
    RedactionContext.consumeReport();
  }

  private Config.GovernanceConfig.RedactionConfig config(
      String mode, double maskThreshold, double blockThreshold) {
    Config.GovernanceConfig.RedactionConfig config = new Config.GovernanceConfig.RedactionConfig();
    config.setMode(mode);
    config.setDetectors(List.of("regex"));
    config.setMaskThreshold(maskThreshold);
    config.setBlockThreshold(blockThreshold);
    return config;
  }
}
