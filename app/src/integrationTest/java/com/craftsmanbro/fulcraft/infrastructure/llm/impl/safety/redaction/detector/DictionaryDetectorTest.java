package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DictionaryDetectorTest {

  @Test
  void detect_matchesDenylist_andRespectsContextAllowlist() {
    DictionaryDetector detector = new DictionaryDetector(false);
    detector.addDenyTerm("apikey");

    DetectionContext ctx = new DetectionContext();

    DetectionResult initial = detector.detect("use apiKey in config", ctx);
    assertThat(initial.hasFindings()).isTrue();

    ctx.setAllowlistTerms(Set.of("APIKEY"));
    DetectionResult allowlisted = detector.detect("use apiKey in config", ctx);
    assertThat(allowlisted.hasFindings()).isFalse();
  }

  @Test
  void detect_skipsAllowlistTermInDetector() {
    DictionaryDetector detector = new DictionaryDetector(false);
    detector.addDenyTerm("secret");
    detector.addAllowTerm("secret");

    DetectionResult result = detector.detect("secret", new DetectionContext());

    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void detect_exactMatchOnly_usesWordBoundaries() {
    DictionaryDetector detector = new DictionaryDetector(true);
    detector.addDenyTerm("key");

    DetectionResult result = detector.detect("monkey key", new DetectionContext());

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).snippet()).isEqualTo("key");
  }

  @Test
  void fromFiles_loadsTermsAndSkipsAllowlisted(@TempDir Path tempDir) throws IOException {
    Path deny = tempDir.resolve("deny.txt");
    Path allow = tempDir.resolve("allow.txt");

    Files.writeString(deny, "secret\n# comment\n");
    Files.writeString(allow, "secret\n");

    DictionaryDetector detector = DictionaryDetector.fromFiles(deny, allow, false);

    assertThat(detector.getDenylistSize()).isEqualTo(1);
    assertThat(detector.getAllowlistSize()).isEqualTo(1);

    DetectionResult result = detector.detect("secret", new DetectionContext());
    assertThat(result.hasFindings()).isFalse();
  }

  @Test
  void isEnabled_requiresDenylistAndDetectorFlagInContext() {
    DictionaryDetector detector = new DictionaryDetector();
    DetectionContext ctx = new DetectionContext();

    assertThat(detector.isEnabled(ctx)).isFalse();

    detector.addDenyTerm("secret");
    ctx.setEnabledDetectors(java.util.List.of("regex"));
    assertThat(detector.isEnabled(ctx)).isFalse();

    ctx.setEnabledDetectors(java.util.List.of("dictionary"));
    assertThat(detector.isEnabled(ctx)).isTrue();
  }

  @Test
  void fromFiles_usesDenylistFileNameInRuleIdAndNormalizesFullWidth(@TempDir Path tempDir)
      throws IOException {
    Path deny = tempDir.resolve("sensitive.txt");
    Files.writeString(deny, "ｓｅｃｒｅｔ\n");

    DictionaryDetector detector = DictionaryDetector.fromFiles(deny, null, false);
    DetectionResult result = detector.detect("secret", new DetectionContext());

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).ruleId()).isEqualTo("dictionary:sensitive.txt");
  }

  @Test
  void detect_partialMatching_canFindOverlappingMatches() {
    DictionaryDetector detector = new DictionaryDetector(false);
    detector.addDenyTerm("aba");

    DetectionResult result = detector.detect("ababa", new DetectionContext());

    assertThat(result.findings()).hasSize(2);
    assertThat(result.findings()).extracting(Finding::snippet).containsExactly("aba", "aba");
  }

  @Test
  void fromFiles_handlesMissingDictionaryFileGracefully(@TempDir Path tempDir) {
    Path missing = tempDir.resolve("missing.txt");

    DictionaryDetector detector = DictionaryDetector.fromFiles(missing, null, false);

    assertThat(detector.getDenylistSize()).isEqualTo(0);
  }
}
