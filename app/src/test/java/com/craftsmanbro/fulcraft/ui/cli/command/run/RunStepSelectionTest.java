package com.craftsmanbro.fulcraft.ui.cli.command.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.craftsmanbro.fulcraft.ui.cli.command.RunCommand;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class RunStepSelectionTest {

  private static final List<String> ANALYZE_ONLY = List.of("analyze");
  private static final List<String> ANALYZE_REPORT_STEPS = List.of("analyze", "report");
  private static final List<String> ANALYZE_REPORT_DOCUMENT_STEPS =
      List.of("analyze", "report", "document");
  private static final List<String> FULL_STEP_ORDER =
      List.of("analyze", "generate", "report", "document");

  @Test
  void resolve_appliesDefaultWhenNoStepsAndNoRange() {
    RunStepSelection stepSelection = stepSelection(null, null, null, ANALYZE_ONLY);

    assertThat(stepSelection.specificSteps()).containsExactly("analyze");
    assertThat(stepSelection.fromStep()).isNull();
    assertThat(stepSelection.toStep()).isNull();
  }

  @Test
  void resolve_normalizesNodeValuesToLowerCase() {
    RunStepSelection stepSelection =
        stepSelection(List.of(" ANALYZE ", "Report"), null, null, null);

    assertThat(stepSelection.specificSteps()).containsExactly("analyze", "report");
  }

  @Test
  void isAnalysisReportOnly_trueWhenAnalyzeAndReportOnly() {
    RunStepSelection stepSelection = stepSelection(ANALYZE_REPORT_STEPS, null, null, null);

    assertThat(stepSelection.isAnalysisReportOnly()).isTrue();
  }

  @Test
  void isAnalysisReportOnly_trueWhenOrderDiffersAndDuplicatesExist() {
    RunStepSelection stepSelection =
        stepSelection(List.of("report", "analyze", "analyze"), null, null, null);

    assertThat(stepSelection.isAnalysisReportOnly()).isTrue();
  }

  @Test
  void isAnalysisReportOnly_falseWhenRangeIsSpecified() {
    RunStepSelection stepSelection = stepSelection(ANALYZE_REPORT_STEPS, "analyze", null, null);

    assertThat(stepSelection.isAnalysisReportOnly()).isFalse();
  }

  @Test
  void isAnalysisReportDocumentOnly_trueWhenAnalyzeReportAndDocumentOnly() {
    RunStepSelection stepSelection = stepSelection(ANALYZE_REPORT_DOCUMENT_STEPS, null, null, null);

    assertThat(stepSelection.isAnalysisReportDocumentOnly()).isTrue();
  }

  @Test
  void isAnalysisReportDocumentOnly_falseWhenUnexpectedStepIncluded() {
    RunStepSelection stepSelection =
        stepSelection(List.of("analyze", "report", "document", "generate"), null, null, null);

    assertThat(stepSelection.isAnalysisReportDocumentOnly()).isFalse();
  }

  @Test
  void validate_rejectsCombiningStepsAndRange() {
    RunStepSelection stepSelection = stepSelection(ANALYZE_ONLY, "analyze", null, null);

    assertThatThrownBy(() -> stepSelection.validate(runCommandSpec(), FULL_STEP_ORDER))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--steps cannot be combined");
  }

  @Test
  void validate_rejectsFromAfterTo() {
    RunStepSelection stepSelection = stepSelection(null, "report", "analyze", null);

    assertThatThrownBy(() -> stepSelection.validate(runCommandSpec(), FULL_STEP_ORDER))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("--from must not be after --to");
  }

  @Test
  void validate_rejectsUnsupportedFromStep() {
    RunStepSelection stepSelection = stepSelection(null, "generate", "report", null);

    assertThatThrownBy(
            () -> stepSelection.validate(runCommandSpec(), ANALYZE_REPORT_DOCUMENT_STEPS))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unsupported --from node: generate");
  }

  @Test
  void validate_rejectsUnsupportedToStep() {
    RunStepSelection stepSelection = stepSelection(null, "analyze", "generate", null);

    assertThatThrownBy(() -> stepSelection.validate(runCommandSpec(), ANALYZE_REPORT_STEPS))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unsupported --to node: generate");
  }

  @Test
  void validate_rejectsUnsupportedSpecificNode() {
    RunStepSelection stepSelection = stepSelection(List.of("missing"), null, null, null);

    assertThatThrownBy(() -> stepSelection.validate(runCommandSpec(), ANALYZE_ONLY))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unsupported --steps node: missing");
  }

  @Test
  void validate_acceptsReportToDocumentRange() {
    RunStepSelection stepSelection = stepSelection(null, "report", "document", null);

    stepSelection.validate(runCommandSpec(), FULL_STEP_ORDER);
  }

  @Test
  void resolveNodesToRun_returnsSpecificWhenProvided() {
    RunStepSelection stepSelection = stepSelection(ANALYZE_REPORT_STEPS, null, null, null);

    assertThat(stepSelection.resolveNodesToRun(ANALYZE_REPORT_DOCUMENT_STEPS))
        .containsExactly("analyze", "report");
  }

  @Test
  void resolveNodesToRun_reordersSpecificStepsToPipelineOrder() {
    RunStepSelection stepSelection = stepSelection(List.of("report", "analyze"), null, null, null);

    assertThat(stepSelection.resolveNodesToRun(ANALYZE_REPORT_DOCUMENT_STEPS))
        .containsExactly("analyze", "report");
  }

  @Test
  void resolveNodesToRun_resolvesRangeWhenProvided() {
    RunStepSelection stepSelection = stepSelection(null, "generate", "report", null);

    assertThat(stepSelection.resolveNodesToRun(FULL_STEP_ORDER))
        .containsExactly("generate", "report");
  }

  @Test
  void validate_rejectsNullSpec() {
    RunStepSelection stepSelection = stepSelection(null, null, null, ANALYZE_ONLY);

    assertThatThrownBy(() -> stepSelection.validate(null, ANALYZE_ONLY))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("@Spec is required");
  }

  @Test
  void validate_rejectsNullStepOrder() {
    RunStepSelection stepSelection = stepSelection(null, null, null, ANALYZE_ONLY);

    assertThatThrownBy(() -> stepSelection.validate(runCommandSpec(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("stepOrder is required");
  }

  private static RunStepSelection stepSelection(
      final List<String> specificSteps,
      final String fromStep,
      final String toStep,
      final List<String> defaultSteps) {
    return RunStepSelection.resolve(specificSteps, fromStep, toStep, defaultSteps);
  }

  private static CommandSpec runCommandSpec() {
    return new CommandLine(new RunCommand()).getCommandSpec();
  }
}
