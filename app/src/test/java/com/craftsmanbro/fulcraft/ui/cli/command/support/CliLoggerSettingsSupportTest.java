package com.craftsmanbro.fulcraft.ui.cli.command.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.craftsmanbro.fulcraft.infrastructure.config.impl.CommonOverrides;
import com.craftsmanbro.fulcraft.ui.cli.UiLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class CliLoggerSettingsSupportTest {
  private boolean originalJsonMode;
  private boolean originalColorEnabled;

  @BeforeEach
  void captureOriginalState() {
    originalJsonMode = UiLogger.isJsonMode();
    originalColorEnabled = UiLogger.isColorEnabled();
  }

  @AfterEach
  void restoreOriginalState() {
    UiLogger.setJsonMode(originalJsonMode);
    UiLogger.setColorEnabled(originalColorEnabled);
  }

  @Test
  void apply_updatesJsonModeFromEffectiveLogFormat() {
    UiLogger.setJsonMode(false);

    CommonOverrides overrides = new CommonOverrides().withLogFormat("json");
    CliLoggerSettingsSupport.apply(overrides);

    assertThat(UiLogger.isJsonMode()).isTrue();
  }

  @Test
  void apply_prioritizesJsonOutputFlagOverLogFormat() {
    UiLogger.setJsonMode(false);

    CommonOverrides overrides = new CommonOverrides().withLogFormat("human").withJsonOutput(true);
    CliLoggerSettingsSupport.apply(overrides);

    assertThat(UiLogger.isJsonMode()).isTrue();
  }

  @Test
  void apply_setsJsonModeFalse_forNonJsonFormat() {
    UiLogger.setJsonMode(true);

    CommonOverrides overrides = new CommonOverrides().withLogFormat("yaml");
    CliLoggerSettingsSupport.apply(overrides);

    assertThat(UiLogger.isJsonMode()).isFalse();
  }

  @Test
  void apply_setsColorForExplicitOnAndOffModes() {
    CommonOverrides on = new CommonOverrides().withColorMode("on");
    CliLoggerSettingsSupport.apply(on);
    assertThat(UiLogger.isColorEnabled()).isTrue();

    CommonOverrides off = new CommonOverrides().withColorMode("off");
    CliLoggerSettingsSupport.apply(off);
    assertThat(UiLogger.isColorEnabled()).isFalse();
  }

  @Test
  void apply_usesConsoleDetection_forUnknownColorMode() {
    boolean expected = System.console() != null;
    CommonOverrides overrides = new CommonOverrides().withColorMode("auto");

    CliLoggerSettingsSupport.apply(overrides);

    assertThat(UiLogger.isColorEnabled()).isEqualTo(expected);
  }

  @Test
  void apply_keepsState_whenOverridesAreNull() {
    UiLogger.setJsonMode(true);
    UiLogger.setColorEnabled(false);

    CliLoggerSettingsSupport.apply(null);

    assertThat(UiLogger.isJsonMode()).isTrue();
    assertThat(UiLogger.isColorEnabled()).isFalse();
  }

  @Test
  void apply_keepsColorState_whenColorModeIsNotProvided() {
    UiLogger.setColorEnabled(false);

    CommonOverrides overrides = new CommonOverrides().withLogFormat("json");
    CliLoggerSettingsSupport.apply(overrides);

    assertThat(UiLogger.isColorEnabled()).isFalse();
  }
}
