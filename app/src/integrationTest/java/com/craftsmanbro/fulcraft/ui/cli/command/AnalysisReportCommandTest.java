package com.craftsmanbro.fulcraft.ui.cli.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.craftsmanbro.fulcraft.Main;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.infrastructure.json.impl.JsonMapperFactory;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.plugins.analysis.contract.AnalysisPort;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.reporting.core.context.ReportMetadataKeys;
import com.craftsmanbro.fulcraft.plugins.reporting.model.ReportData;
import com.craftsmanbro.fulcraft.testsupport.KernelPortTestExtension;
import com.craftsmanbro.fulcraft.ui.cli.wiring.ServiceFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(KernelPortTestExtension.class)
class AnalysisReportCommandTest {

  @TempDir Path tempDir;

  @Test
  void doCall_generatesReportsFromExistingRunAndAppliesFormatOverride() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    String runId = "run-success";
    Path analysisDir = createAnalysisDirectory(runId);
    writeAnalysisResult(
        analysisDir.resolve("analysis_1.json"),
        createClassOnlyAnalysisResult("com.example.report.TargetClass"));

    Path outputFile = tempDir.resolve("reports").resolve("custom-summary.json");
    new CommandLine(command)
        .parseArgs("--run-id", runId, "--format", " JSON ", "--output", outputFile.toString());

    Config config = new Config();
    config.getOutput().getFormat().setReport("markdown");

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(config.getOutput().getReportFormat()).isEqualTo("json");
    assertThat(outputFile).exists();
    assertThat(outputFile.getParent().resolve("report.md")).doesNotExist();
    assertThat(outputFile.getParent().resolve("analysis_visual.html")).exists();
  }

  @Test
  void doCall_returnsZeroWithoutGeneratingReport_whenAnalysisHasNoClasses() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    String runId = "run-empty";
    Path analysisDir = createAnalysisDirectory(runId);
    writeAnalysisResult(analysisDir.resolve("analysis_empty.json"), new AnalysisResult());
    new CommandLine(command).parseArgs("--run-id", runId);

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isZero();
    assertThat(tempDir.resolve(".ful").resolve("runs").resolve(runId).resolve("report"))
        .doesNotExist();
  }

  @Test
  void doCall_returnsOne_whenReportOutputDirectoryCannotBeCreated() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    String runId = "run-write-failure";
    Path analysisDir = createAnalysisDirectory(runId);
    writeAnalysisResult(
        analysisDir.resolve("analysis_1.json"),
        createClassOnlyAnalysisResult("com.example.report.BrokenOutput"));

    Path blockedParent = tempDir.resolve("blocked-parent");
    Files.writeString(blockedParent, "not a directory");
    Path outputFile = blockedParent.resolve("summary.json");

    new CommandLine(command).parseArgs("--run-id", runId, "--output", outputFile.toString());

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
    assertThat(blockedParent).isRegularFile();
  }

  @Test
  void doCall_returnsOneAndPrintsStackTrace_whenVerboseAndReportOutputDirectoryCannotBeCreated()
      throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    String runId = "run-write-failure-verbose";
    Path analysisDir = createAnalysisDirectory(runId);
    writeAnalysisResult(
        analysisDir.resolve("analysis_1.json"),
        createClassOnlyAnalysisResult("com.example.report.BrokenOutputVerbose"));

    Path blockedParent = tempDir.resolve("blocked-parent-verbose");
    Files.writeString(blockedParent, "not a directory");
    Path outputFile = blockedParent.resolve("summary.json");

    new CommandLine(command)
        .parseArgs("--run-id", runId, "--output", outputFile.toString(), "--verbose");

    int exitCode = command.doCall(new Config(), tempDir);

    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void doCall_throwsParameterException_whenRunDirectoryDoesNotExist() {
    AnalysisReportCommand command = new AnalysisReportCommand();
    new CommandLine(command).parseArgs("--run-id", "missing-run");

    assertThatThrownBy(() -> command.doCall(new Config(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("missing-run");
  }

  @Test
  void doCall_throwsParameterException_whenAnalysisDirectoryDoesNotExist() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    String runId = "run-missing-analysis-dir";
    Files.createDirectories(tempDir.resolve(".ful").resolve("runs").resolve(runId));
    new CommandLine(command).parseArgs("--run-id", runId);

    assertThatThrownBy(() -> command.doCall(new Config(), tempDir))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("analysis");
  }

  @Test
  void doCall_generatesReportsFromLiveAnalysis_whenRunIdIsNotProvided() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    AnalysisPort analysisPort = mock(AnalysisPort.class);
    ServiceFactory serviceFactory = mock(ServiceFactory.class);
    Main main = mock(Main.class);
    Config config = Config.createDefault();
    when(main.getServices()).thenReturn(serviceFactory);
    when(serviceFactory.createAnalysisPort("composite")).thenReturn(analysisPort);
    when(analysisPort.analyze(tempDir, config))
        .thenReturn(createClassOnlyAnalysisResult("com.example.live.Generated"));
    command.main = main;

    Path outputFile = tempDir.resolve("live-report").resolve("summary.json");
    new CommandLine(command).parseArgs("--output", outputFile.toString());

    int exitCode = command.doCall(config, tempDir);

    assertThat(exitCode).isZero();
    assertThat(outputFile).exists();
    assertThat(outputFile.getParent().resolve("analysis_visual.html")).exists();
    assertThat(tempDir.resolve(".ful").resolve("runs")).isDirectory();
  }

  @Test
  void loadAnalysisResult_aggregatesClassesFromFiles() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Config config = new Config();
    String runId = "run-1";
    Path analysisDir = tempDir.resolve(".ful").resolve("runs").resolve(runId).resolve("analysis");
    Files.createDirectories(analysisDir);

    ObjectMapper mapper = JsonMapperFactory.create();

    AnalysisResult first = new AnalysisResult();
    ClassInfo classA = new ClassInfo();
    classA.setFqn("com.example.A");
    first.setClasses(List.of(classA));
    mapper.writeValue(analysisDir.resolve("analysis_1.json").toFile(), first);

    AnalysisResult second = new AnalysisResult();
    ClassInfo classB = new ClassInfo();
    classB.setFqn("com.example.B");
    second.setClasses(List.of(classB));
    mapper.writeValue(analysisDir.resolve("analysis_2.json").toFile(), second);

    AnalysisResult loaded = invokeLoadAnalysisResult(command, config, tempDir, runId);

    assertThat(loaded.getClasses())
        .extracting(ClassInfo::getFqn)
        .containsExactlyInAnyOrder("com.example.A", "com.example.B");
  }

  @Test
  void loadAnalysisResult_skipsInvalidAnalysisJsonFile() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Config config = new Config();
    String runId = "run-invalid-json";
    Path analysisDir = createAnalysisDirectory(runId);

    writeAnalysisResult(
        analysisDir.resolve("analysis_valid.json"),
        createClassOnlyAnalysisResult("com.example.ValidOnly"));
    Files.writeString(analysisDir.resolve("analysis_broken.json"), "{invalid-json");

    AnalysisResult loaded = invokeLoadAnalysisResult(command, config, tempDir, runId);

    assertThat(loaded.getClasses())
        .extracting(ClassInfo::getFqn)
        .containsExactly("com.example.ValidOnly");
  }

  @Test
  void buildReportData_defaultsProjectIdAndCountsMethods() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId(" ");
    config.setProject(projectConfig);

    RunContext context = new RunContext(tempDir, config, "run-9");

    MethodInfo first = new MethodInfo();
    MethodInfo second = new MethodInfo();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setMethods(List.of(first, second));

    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of(classInfo));

    ReportData reportData = invokeBuildReportData(command, context, analysisResult);

    assertThat(reportData.getProjectId()).isEqualTo("unknown");
    assertThat(reportData.getTotalClassesAnalyzed()).isEqualTo(1);
    assertThat(reportData.getTotalMethodsAnalyzed()).isEqualTo(2);
  }

  @Test
  void buildReportData_includesAnalysisHumanSummaryFromContextMetadata() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Config config = new Config();
    Config.ProjectConfig projectConfig = new Config.ProjectConfig();
    projectConfig.setId("my-project");
    config.setProject(projectConfig);

    RunContext context = new RunContext(tempDir, config, "run-10");
    context.putMetadata(ReportMetadataKeys.ANALYSIS_HUMAN_SUMMARY, "LLM summary");

    MethodInfo methodInfo = new MethodInfo();
    ClassInfo classInfo = new ClassInfo();
    classInfo.setMethods(List.of(methodInfo));
    AnalysisResult analysisResult = new AnalysisResult();
    analysisResult.setClasses(List.of(classInfo));

    ReportData reportData = invokeBuildReportData(command, context, analysisResult);

    assertThat(reportData.getAnalysisHumanSummary()).isEqualTo("LLM summary");
  }

  @Test
  void optionsAndAccessors_returnParsedValues() {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Path projectRoot = tempDir.resolve("target-root");
    new CommandLine(command)
        .parseArgs(
            "--project-root",
            projectRoot.toString(),
            "--files",
            "A.java,B.java",
            "--dirs",
            "src/main/java,src/test/java",
            "--exclude-tests",
            "--verbose");

    assertThat(command.getProjectRootOption()).isEqualTo(projectRoot);
    assertThat(command.getProjectRootPositional()).isNull();
    assertThat(command.getFiles()).containsExactly("A.java", "B.java");
    assertThat(command.getDirs()).containsExactly("src/main/java", "src/test/java");
    assertThat(command.getExcludeTests()).contains(true);
    assertThat(command.isVerboseEnabled()).isTrue();
  }

  @Test
  void resourceBundleSetter_andApplyFormatOverrideNullConfig_workAsExpected() throws Exception {
    AnalysisReportCommand command = new AnalysisReportCommand();
    ResourceBundle bundle = ResourceBundle.getBundle("messages");
    command.setResourceBundle(bundle);

    Field resourceBundleField = AnalysisReportCommand.class.getDeclaredField("resourceBundle");
    resourceBundleField.setAccessible(true);
    assertThat(resourceBundleField.get(command)).isSameAs(bundle);

    new CommandLine(command).parseArgs("--format", "JSON");
    Method applyFormatOverride =
        AnalysisReportCommand.class.getDeclaredMethod("applyFormatOverride", Config.class);
    applyFormatOverride.setAccessible(true);
    applyFormatOverride.invoke(command, new Object[] {null});
  }

  @Test
  void projectRootPositionalAccessor_returnsPositionalArgument() {
    AnalysisReportCommand command = new AnalysisReportCommand();
    Path positionalRoot = tempDir.resolve("positional-root");
    new CommandLine(command).parseArgs(positionalRoot.toString());

    assertThat(command.getProjectRootPositional()).isEqualTo(positionalRoot);
    assertThat(command.getProjectRootOption()).isNull();
  }

  private Path createAnalysisDirectory(String runId) throws Exception {
    Path analysisDir = tempDir.resolve(".ful").resolve("runs").resolve(runId).resolve("analysis");
    Files.createDirectories(analysisDir);
    return analysisDir;
  }

  private static void writeAnalysisResult(Path outputFile, AnalysisResult result) throws Exception {
    ObjectMapper mapper = JsonMapperFactory.create();
    mapper.writeValue(outputFile.toFile(), result);
  }

  private static AnalysisResult createClassOnlyAnalysisResult(String fqn) {
    ClassInfo classInfo = new ClassInfo();
    classInfo.setFqn(fqn);
    AnalysisResult result = new AnalysisResult();
    result.setClasses(List.of(classInfo));
    return result;
  }

  private static AnalysisResult invokeLoadAnalysisResult(
      AnalysisReportCommand command, Config config, Path projectRoot, String runId)
      throws Exception {
    Method method =
        AnalysisReportCommand.class.getDeclaredMethod(
            "loadAnalysisResult", Config.class, Path.class, String.class);
    method.setAccessible(true);
    return (AnalysisResult) method.invoke(command, config, projectRoot, runId);
  }

  private static ReportData invokeBuildReportData(
      AnalysisReportCommand command, RunContext context, AnalysisResult result) throws Exception {
    Method method =
        AnalysisReportCommand.class.getDeclaredMethod(
            "buildReportData", RunContext.class, AnalysisResult.class);
    method.setAccessible(true);
    return (ReportData) method.invoke(command, context, result);
  }
}
