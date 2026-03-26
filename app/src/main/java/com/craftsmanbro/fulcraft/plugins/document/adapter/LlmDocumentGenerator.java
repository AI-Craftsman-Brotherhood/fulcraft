package com.craftsmanbro.fulcraft.plugins.document.adapter;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.llm.contract.LlmClientPort;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.PromptRedactionService;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.RedactionResult;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.plugins.analysis.model.AnalysisResult;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import com.craftsmanbro.fulcraft.plugins.document.contract.DocumentGenerator;
import com.craftsmanbro.fulcraft.plugins.document.core.util.DocumentUtils;
import com.craftsmanbro.fulcraft.plugins.document.core.util.PromptInputCanonicalizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates detailed design documentation using LLM enhancement.
 *
 * <p>Takes the static analysis information and uses LLM to generate human-readable detailed design
 * documentation in the configured locale.
 */
public class LlmDocumentGenerator implements DocumentGenerator {

  private static final String FORMAT = "llm";

  private static final String EXTENSION = "_detail.md";

  private static final String DOCUMENT_VALUE_NA = "document.value.na";

  private static final String LEADING_BULLET_PATTERN = "^-\\s*";

  private static final Pattern METHOD_HEADING_PATTERN =
      Pattern.compile("(?m)^###\\s*3\\.\\d+\\s+(.+?)\\s*$");

  private static final Pattern SECTION_FOUR_HEADING_PATTERN = Pattern.compile("^##\\s*4[.\\s].*$");

  private static final Pattern SUBSECTION_HEADING_PATTERN =
      Pattern.compile("^####\\s+3\\.\\d+\\.(\\d+)\\s+.*$");

  private static final Pattern PATH_ID_TOKEN_PATTERN =
      Pattern.compile("\\b(path-[a-z0-9_-]+)\\b", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_LABEL_BACKTICK_PATTERN =
      Pattern.compile("`(path-[a-z0-9_-]+)(?:\\s*[:：]\\s*([^`]+))?`", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_LABEL_BRACKET_PATTERN =
      Pattern.compile(
          "\\[(path-[a-z0-9_-]+)\\]\\s*([^\\r\\n]*?)\\s*(?:->\\s*.+)?$", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_OUTCOME_RESULT_PATTERN =
      Pattern.compile("結果\\s*[:：]\\s*(.+?)\\s*$");

  private static final Pattern PATH_OUTCOME_PAREN_PATTERN =
      Pattern.compile("結果\\s*[（(]\\s*(.+?)\\s*[）)]");

  private static final Pattern PATH_OUTCOME_EN_PATTERN =
      Pattern.compile("(?:result|outcome)\\s*[:：]\\s*(.+?)\\s*$", Pattern.CASE_INSENSITIVE);

  private static final Pattern PATH_OUTCOME_ARROW_PATTERN = Pattern.compile("->\\s*(.+?)\\s*$");

  private static final Pattern MALFORMED_INLINE_INPUT_NONE_PATTERN =
      Pattern.compile(
          "^-\\s*((?:入力/出力|入出力|入力|inputs?/outputs?|inputs?))\\s*[:：]\\s*-\\s*(なし|none)\\s*$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern VERIFIED_FALSE_TOKEN_PATTERN =
      Pattern.compile("verified\\s*=\\s*false", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern CONFIDENCE_LT_ONE_TOKEN_PATTERN =
      Pattern.compile("confidence\\s*<\\s*1\\.0", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final List<String> REQUIRED_EXTERNAL_SPEC_LABELS_JA =
      List.of("クラス名", "パッケージ", "ファイルパス", "クラス種別", "継承", "実装インターフェース");

  private static final List<String> REQUIRED_EXTERNAL_SPEC_LABELS_EN =
      List.of("Class Name", "Package", "File Path", "Class Type", "Extends", "Implements");

  private final LlmClientPort llmClient;

  private final PromptRedactionService promptRedactionService;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmDocumentBatchProcessor
      batchProcessor;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmGeneratedDocumentPostProcessor
      generatedDocumentPostProcessor;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmListStructureNormalizer
      listStructureNormalizer;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapInspector
      analysisGapInspector;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmOpenQuestionBuilder
      openQuestionBuilder;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmPromptContextFactory
      promptContextFactory;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
          .LlmPathConsistencyValidator
      pathConsistencyValidator;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
          .LlmPathConditionHeuristics
      pathConditionHeuristics;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmCalledMethodFilter
      calledMethodFilter;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmMethodsInfoFormatter
      methodsInfoFormatter;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmRetryPromptBuilder
      retryPromptBuilder;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
          .LlmDynamicResolutionEvaluator
      dynamicResolutionEvaluator;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
          .LlmMethodFlowFactsExtractor
      methodFlowFactsExtractor;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
          .LlmFallbackPreconditionExtractor
      fallbackPreconditionExtractor;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmFallbackPathSectionBuilder
      fallbackPathSectionBuilder;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmFallbackPurposeComposer
      fallbackPurposeComposer;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmConstructorSemantics
      constructorSemantics;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
          .LlmMethodSectionValidator
      methodSectionValidator;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
          .LlmDocumentContentValidator
      contentValidator;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmFallbackDocumentBuilder
      fallbackDocumentBuilder;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .LlmInterfaceDocumentBuilder
      interfaceDocumentBuilder;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmPromptTemplateLoader
      promptTemplateLoader;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier
      methodDocClassifier;

  private final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
          .TemplateMethodRenderer
      templateMethodRenderer;

  private final Map<String, GenerationDecision> generationDecisions = new LinkedHashMap<>();

  public LlmDocumentGenerator(final LlmClientPort llmClient) {
    this(
        llmClient,
        new PromptRedactionService(),
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmDocumentBatchProcessor());
  }

  public LlmDocumentGenerator(
      final LlmClientPort llmClient, final PromptRedactionService promptRedactionService) {
    this(
        llmClient,
        promptRedactionService,
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmDocumentBatchProcessor());
  }

  LlmDocumentGenerator(
      final LlmClientPort llmClient,
      final PromptRedactionService promptRedactionService,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation.LlmDocumentBatchProcessor
          batchProcessor) {
    this.llmClient = llmClient;
    this.promptRedactionService = promptRedactionService;
    this.batchProcessor = batchProcessor;
    this.generatedDocumentPostProcessor =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmGeneratedDocumentPostProcessor();
    this.listStructureNormalizer =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmListStructureNormalizer();
    this.analysisGapInspector =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapInspector();
    this.openQuestionBuilder =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmOpenQuestionBuilder();
    this.dynamicResolutionEvaluator =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
            .LlmDynamicResolutionEvaluator();
    this.methodFlowFactsExtractor =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
            .LlmMethodFlowFactsExtractor();
    this.promptContextFactory =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmPromptContextFactory(
            dynamicResolutionEvaluator::isResolutionUncertain,
            dynamicResolutionEvaluator::isResolutionOpenQuestion,
            dynamicResolutionEvaluator::isResolutionKnownMissing,
            new com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier());
    this.pathConsistencyValidator =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
            .LlmPathConsistencyValidator(msg(DOCUMENT_VALUE_NA));
    this.pathConditionHeuristics =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
            .LlmPathConditionHeuristics();
    this.calledMethodFilter =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis.LlmCalledMethodFilter(
            msg(DOCUMENT_VALUE_NA));
    this.methodsInfoFormatter =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmMethodsInfoFormatter(
            this::msg,
            this.calledMethodFilter,
            dynamicResolutionEvaluator::isResolutionOpenQuestion,
            dynamicResolutionEvaluator::isResolutionKnownMissing,
            dynamicResolutionEvaluator::readVerifiedFlag);
    this.retryPromptBuilder =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmRetryPromptBuilder();
    this.fallbackPreconditionExtractor =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.analysis
            .LlmFallbackPreconditionExtractor(
            pathConditionHeuristics::isLikelyFlowCondition,
            pathConditionHeuristics::isLikelyErrorIndicator);
    this.fallbackPathSectionBuilder =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmFallbackPathSectionBuilder(
            this.fallbackPreconditionExtractor,
            dynamicResolutionEvaluator::hasOpenQuestionDynamicResolution,
            dynamicResolutionEvaluator::hasKnownMissingDynamicResolution,
            this::resolveMethodDisplayName,
            this::isFailureFactoryMethodName,
            pathConditionHeuristics::isLikelyErrorIndicator,
            methodFlowFactsExtractor::isEarlyReturnIncompatible,
            methodFlowFactsExtractor::collectSwitchCaseFacts,
            msg(DOCUMENT_VALUE_NA));
    this.fallbackPurposeComposer =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmFallbackPurposeComposer();
    this.constructorSemantics =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmConstructorSemantics();
    this.contentValidator =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
            .LlmDocumentContentValidator();
    this.fallbackDocumentBuilder =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmFallbackDocumentBuilder();
    this.interfaceDocumentBuilder =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmInterfaceDocumentBuilder();
    this.promptTemplateLoader =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.context.LlmPromptTemplateLoader();
    this.methodSectionValidator =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
            .LlmMethodSectionValidator(
            this::resolveMethodDisplayName,
            com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
                ::normalizeMethodName,
            dynamicResolutionEvaluator::hasUncertainDynamicResolution,
            this::isFailureFactoryMethodName,
            this.fallbackPreconditionExtractor::containsUnsupportedNoArgPrecondition,
            this.fallbackPreconditionExtractor::containsUnsupportedNonNullPreconditionAssumption,
            this.fallbackPreconditionExtractor::collectSourceBackedPreconditions,
            methodFlowFactsExtractor::isEarlyReturnIncompatible,
            methodFlowFactsExtractor::collectSwitchCaseFacts);
    this.methodDocClassifier =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.MethodDocClassifier();
    this.templateMethodRenderer =
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation.TemplateMethodRenderer(
            this.methodDocClassifier);
  }

  @Override
  public int generate(final AnalysisResult result, final Path outputDir, final Config config)
      throws IOException {
    Objects.requireNonNull(
        result,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "result must not be null"));
    Objects.requireNonNull(
        outputDir,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "outputDir must not be null"));
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "config must not be null"));
    generationDecisions.clear();
    final Set<String> crossClassKnownMethodNames = collectKnownMethodNames(result.getClasses());
    final String renderMode =
        config.getDocs() != null ? config.getDocs().getMethodRenderMode() : "legacy";
    final com.craftsmanbro.fulcraft.plugins.document.core.llm.generation.LlmDocumentBatchProcessor
            .BatchResult
        batchResult =
            batchProcessor.generate(
                result,
                outputDir,
                config.getLlm(),
                crossClassKnownMethodNames,
                EXTENSION,
                (classInfo, llmConfig, knownMethods) ->
                    generateDetailedDocument(classInfo, llmConfig, knownMethods, renderMode, true));
    logGenerationModeSummary();
    if (!batchResult.failedClassNames().isEmpty()) {
      Logger.warn(
          msg(
              "report.docs.llm.completed_with_failures",
              batchResult.generatedCount(),
              batchResult.totalCount(),
              String.join(", ", batchResult.failedClassNames())));
    }
    return batchResult.generatedCount();
  }

  @Override
  public String getFormat() {
    return FORMAT;
  }

  @Override
  public String getFileExtension() {
    return EXTENSION;
  }

  /**
   * Generates detailed design documentation for a class using LLM.
   *
   * @param classInfo the class information from static analysis
   * @param llmConfig the LLM configuration
   * @return the generated Markdown documentation
   */
  public String generateDetailedDocument(
      final ClassInfo classInfo, final Config.LlmConfig llmConfig) {
    return generateDetailedDocument(classInfo, llmConfig, Set.of(), "legacy", false);
  }

  private String generateDetailedDocument(
      final ClassInfo classInfo,
      final Config.LlmConfig llmConfig,
      final Set<String> crossClassKnownMethodNames,
      final String renderMode,
      final boolean structuralRepairEnabled) {
    final boolean hybrid = "hybrid".equalsIgnoreCase(renderMode);
    Objects.requireNonNull(
        classInfo,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "classInfo must not be null"));
    Objects.requireNonNull(
        llmConfig,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "document.common.error.argument_null", "llmConfig must not be null"));
    final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext =
        buildPromptContext(classInfo, crossClassKnownMethodNames);
    if (classInfo.isInterface()) {
      Logger.info(msg("report.docs.llm.interface_bypassed", classInfo.getFqn()));
      final String interfaceDocument = buildInterfaceDocument(classInfo, promptContext);
      recordGenerationDecision(classInfo.getFqn(), GenerationMode.INTERFACE, List.of());
      return interfaceDocument;
    }
    Logger.info(MessageSource.getMessage("report.docs.llm.detail_start", classInfo.getFqn()));
    String result =
        generatedDocumentPostProcessor.sanitize(callLlm(promptContext.prompt(), llmConfig));
    result = assembleDocument(result, classInfo, promptContext, hybrid, structuralRepairEnabled);
    final ValidationResult validationResult = validateDocument(result, promptContext);
    if (validationResult.valid()) {
      recordGenerationDecision(classInfo.getFqn(), GenerationMode.DIRECT, List.of());
      return result;
    }
    Logger.warn(
        msg(
            "report.docs.llm.validation_failed",
            classInfo.getFqn(),
            String.join("; ", validationResult.reasons())));
    final String retryPrompt =
        retryPromptBuilder.buildRetryPrompt(
            promptContext.prompt(),
            validationResult.reasons(),
            promptContext.validationFacts().methodNames().size(),
            isJapaneseLocale());
    String retryResult = generatedDocumentPostProcessor.sanitize(callLlm(retryPrompt, llmConfig));
    retryResult =
        assembleDocument(retryResult, classInfo, promptContext, hybrid, structuralRepairEnabled);
    final ValidationResult retryValidation = validateDocument(retryResult, promptContext);
    if (retryValidation.valid()) {
      recordGenerationDecision(
          classInfo.getFqn(), GenerationMode.RETRY, List.copyOf(validationResult.reasons()));
      return retryResult;
    }
    Logger.warn(
        msg(
            "report.docs.llm.validation_retry_failed",
            classInfo.getFqn(),
            String.join("; ", retryValidation.reasons())));
    recordGenerationDecision(
        classInfo.getFqn(), GenerationMode.FALLBACK, List.copyOf(retryValidation.reasons()));
    return buildFallbackDocument(classInfo, promptContext, retryValidation.reasons());
  }

  private com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext buildPromptContext(
      final ClassInfo classInfo, final Set<String> crossClassKnownMethodNames) {
    return promptContextFactory.buildPromptContext(
        classInfo, crossClassKnownMethodNames, promptTemplate(), this::buildMethodsInfo);
  }

  private String buildMethodsInfo(
      final List<MethodInfo> methods,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts
          validationFacts) {
    return methodsInfoFormatter.buildMethodsInfo(methods, validationFacts);
  }

  private String buildClassAttributes(final ClassInfo classInfo) {
    return promptContextFactory.buildClassAttributes(classInfo);
  }

  private Set<String> collectKnownMethodNames(final List<ClassInfo> classes) {
    return promptContextFactory.collectKnownMethodNames(classes);
  }

  private String buildCautionsInfo(final List<MethodInfo> methods) {
    return promptContextFactory.buildCautionsInfo(methods);
  }

  private String resolveMethodDisplayName(final MethodInfo method) {
    return promptContextFactory.resolveMethodDisplayName(method);
  }

  private String callLlm(final String prompt, final Config.LlmConfig llmConfig) {
    final RedactionResult redactionResult = promptRedactionService.redactPrompt(prompt);
    try {
      return llmClient.generateTest(redactionResult.redactedText(), llmConfig);
    } finally {
      llmClient.clearContext();
    }
  }

  private ValidationResult validateDocument(
      final String document,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext) {
    final List<String> reasons = new ArrayList<>();
    final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts facts =
        promptContext.validationFacts();
    contentValidator.validate(
        document,
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.validation
            .LlmDocumentContentValidator.ValidationContext(
            facts.methodNames(),
            facts.deadCodeMethods(),
            facts.duplicateMethods(),
            facts.uncertainDynamicMethodNames(),
            facts.uncertainDynamicMethodDisplayNames(),
            facts.knownMissingDynamicMethodNames(),
            facts.knownMissingDynamicMethodDisplayNames(),
            facts.knownMethodNames(),
            facts.knownConstructorSignatures(),
            facts.hasAnyCautions(),
            facts.nestedClass()),
        reasons,
        isJapaneseLocale());
    pathConsistencyValidator.validate(document, reasons, isJapaneseLocale());
    methodSectionValidator.validate(
        document, promptContext.specMethods(), reasons, isJapaneseLocale(), msg(DOCUMENT_VALUE_NA));
    return new ValidationResult(reasons.isEmpty(), reasons);
  }

  private boolean isFailureFactoryMethodName(final String methodName) {
    final String normalized =
        com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
            .normalizeMethodName(methodName);
    return "failure".equals(normalized) || "fail".equals(normalized) || "error".equals(normalized);
  }

  private String buildFallbackDocument(
      final ClassInfo classInfo,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final List<String> validationReasons) {
    final boolean japanese = isJapaneseLocale();
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String packageName =
        DocumentUtils.formatPackageNameForDisplay(DocumentUtils.getPackageName(classInfo));
    final String filePath = nullSafe(classInfo.getFilePath());
    final String classType = DocumentUtils.buildClassType(classInfo);
    final String extendsInfo = formatExtendsInfo(classInfo);
    final String implementsInfo = formatImplementsInfo(classInfo);
    final String classAttributes = buildClassAttributes(classInfo);
    final String fieldsInfo = DocumentUtils.buildFieldsInfo(classInfo.getFields());
    final List<
            com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
                .LlmFallbackDocumentBuilder.FallbackMethodSection>
        methodSections = new ArrayList<>();
    final List<MethodInfo> sortedMethods =
        PromptInputCanonicalizer.sortMethods(promptContext.specMethods());
    final List<String> purposeLines =
        fallbackPurposeComposer.composePurposeLines(classInfo, sortedMethods, japanese);
    for (final MethodInfo method : sortedMethods) {
      final String methodName = resolveMethodDisplayName(method);
      final String signature = nullSafe(method.getSignature());
      final List<String> calledMethods =
          calledMethodFilter.filterCalledMethodsForSpecificationWithArgumentLiterals(
              method, promptContext.validationFacts());
      final List<String> preconditions =
          fallbackPreconditionExtractor.collectFallbackPreconditions(method);
      final List<String> postconditions =
          fallbackPathSectionBuilder.collectFallbackPostconditions(
              method, japanese, promptContext.validationFacts());
      final List<String> normalFlows =
          fallbackPathSectionBuilder.collectFallbackNormalFlows(
              method, calledMethods, japanese, promptContext.validationFacts());
      final List<String> errorBoundaries =
          fallbackPathSectionBuilder.collectFallbackErrorBoundaries(
              method, japanese, promptContext.validationFacts());
      final List<String> testViewpoints =
          fallbackPathSectionBuilder.collectFallbackTestViewpoints(
              method, japanese, promptContext.validationFacts());
      methodSections.add(
          new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
              .LlmFallbackDocumentBuilder.FallbackMethodSection(
              methodName,
              signature,
              preconditions,
              postconditions,
              normalFlows,
              errorBoundaries,
              calledMethods,
              testViewpoints));
    }
    final List<String> openQuestions =
        openQuestionBuilder.buildFallbackOpenQuestions(
            promptContext.validationFacts().methodNames(),
            promptContext.validationFacts().uncertainDynamicMethodDisplayNames(),
            validationReasons,
            japanese);
    return fallbackDocumentBuilder.build(
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmFallbackDocumentBuilder.FallbackDocumentInput(
            japanese,
            className,
            purposeLines,
            packageName,
            filePath,
            classInfo.getLoc(),
            promptContext.specMethods().size(),
            classType,
            extendsInfo,
            implementsInfo,
            classAttributes,
            fieldsInfo,
            methodSections,
            buildCautionsInfo(promptContext.specMethods()),
            openQuestions));
  }

  private String buildInterfaceDocument(
      final ClassInfo classInfo,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext) {
    final boolean japanese = isJapaneseLocale();
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String packageName =
        DocumentUtils.formatPackageNameForDisplay(DocumentUtils.getPackageName(classInfo));
    final String filePath = nullSafe(classInfo.getFilePath());
    final String classType = DocumentUtils.buildClassType(classInfo);
    final String extendsInfo = formatExtendsInfo(classInfo);
    final String implementsInfo = formatImplementsInfo(classInfo);
    final String classAttributes = buildClassAttributes(classInfo);
    final String fieldsInfo = DocumentUtils.buildFieldsInfo(classInfo.getFields());
    final List<
            com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
                .LlmInterfaceDocumentBuilder.InterfaceMethodSection>
        methodSections = new ArrayList<>();
    final List<MethodInfo> sortedMethods =
        PromptInputCanonicalizer.sortMethods(promptContext.specMethods());
    for (final MethodInfo method : sortedMethods) {
      methodSections.add(
          new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
              .LlmInterfaceDocumentBuilder.InterfaceMethodSection(
              resolveMethodDisplayName(method),
              nullSafe(method.getSignature()),
              PromptInputCanonicalizer.sortStrings(method.getThrownExceptions())));
    }
    final List<String> openQuestions =
        openQuestionBuilder.buildInterfaceOpenQuestions(
            promptContext.validationFacts().uncertainDynamicMethodDisplayNames(), japanese);
    return interfaceDocumentBuilder.build(
        new com.craftsmanbro.fulcraft.plugins.document.core.llm.generation
            .LlmInterfaceDocumentBuilder.InterfaceDocumentInput(
            japanese,
            className,
            packageName,
            filePath,
            classInfo.getLoc(),
            promptContext.specMethods().size(),
            classType,
            extendsInfo,
            implementsInfo,
            classAttributes,
            fieldsInfo,
            methodSections,
            buildCautionsInfo(promptContext.specMethods()),
            openQuestions));
  }

  /**
   * Assembles the final document from LLM output. In hybrid mode, always rebuilds to insert
   * template-generated method sections. In legacy mode, only rebuilds if structural repair is
   * needed.
   */
  private String assembleDocument(
      final String document,
      final ClassInfo classInfo,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final boolean hybrid,
      final boolean structuralRepairEnabled) {
    if (document == null || document.isBlank() || classInfo == null || promptContext == null) {
      return document == null ? "" : document;
    }
    // In hybrid mode, always rebuild to insert template methods.
    // In legacy mode, only rebuild if structural repair is enabled and needed.
    if (hybrid || (structuralRepairEnabled && requiresStructuralRepair(document, promptContext))) {
      return generatedDocumentPostProcessor.sanitize(
          buildRepairedDocument(document, classInfo, promptContext, hybrid));
    }
    return document;
  }

  private boolean requiresStructuralRepair(
      final String document,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext) {
    if (document == null || document.isBlank() || promptContext == null) {
      return true;
    }
    final int expectedMethodHeadings = promptContext.validationFacts().methodNames().size();
    if (countMethodHeadings(document) != expectedMethodHeadings) {
      return true;
    }
    if (!hasRequiredExternalMetadata(document)) {
      return true;
    }
    if (containsGenericOpenQuestionTemplate(document)) {
      return true;
    }
    if (hasAnalysisGapWithNoneOpenQuestions(document)) {
      return true;
    }
    if (hasUnsupportedNoArgPreconditionContent(document, promptContext.specMethods())) {
      return true;
    }
    if (hasMissingSourceBackedPreconditions(document, promptContext.specMethods())) {
      return true;
    }
    if (listStructureNormalizer.hasNonCanonicalOrderedList(document)) {
      return true;
    }
    return hasPathConsistencyIssues(document);
  }

  private boolean containsGenericOpenQuestionTemplate(final String document) {
    if (document == null || document.isBlank()) {
      return false;
    }
    for (final String line : splitNonBlankLines(document)) {
      if (isGenericOpenQuestionTemplateLine(
          com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.normalizeLine(
              line))) {
        return true;
      }
    }
    return false;
  }

  private boolean isGenericOpenQuestionTemplateLine(final String normalizedLine) {
    if (normalizedLine == null || normalizedLine.isBlank()) {
      return false;
    }
    final boolean hasDynamicResolutions = normalizedLine.contains("dynamic_resolutions");
    final boolean hasVerifiedFalse = VERIFIED_FALSE_TOKEN_PATTERN.matcher(normalizedLine).find();
    final boolean hasConfidenceLtOne =
        CONFIDENCE_LT_ONE_TOKEN_PATTERN.matcher(normalizedLine).find();
    final boolean hasUncertainMarker =
        normalizedLine.contains("未確定") || normalizedLine.contains("不明");
    return (hasDynamicResolutions && (hasVerifiedFalse || hasConfidenceLtOne))
        || ((hasDynamicResolutions || hasVerifiedFalse || hasConfidenceLtOne)
            && hasUncertainMarker);
  }

  private boolean hasPathConsistencyIssues(final String document) {
    if (document == null || document.isBlank()) {
      return false;
    }
    final List<String> reasons = new ArrayList<>();
    pathConsistencyValidator.validate(document, reasons, isJapaneseLocale());
    return !reasons.isEmpty();
  }

  private boolean hasAnalysisGapWithNoneOpenQuestions(final String document) {
    if (document == null || document.isBlank()) {
      return false;
    }
    final String openQuestionsSection = extractTopLevelSectionBody(document, 6);
    if (openQuestionsSection.isBlank() || !isNoneOnlySection(openQuestionsSection)) {
      return false;
    }
    return analysisGapInspector.hasGapInMainSections(document);
  }

  private boolean isNoneOnlySection(final String sectionBody) {
    if (sectionBody == null || sectionBody.isBlank()) {
      return false;
    }
    final List<String> lines = splitNonBlankLines(sectionBody);
    if (lines.size() != 1) {
      return false;
    }
    final String normalized = normalizeLineWithoutLeadingBullet(lines.get(0));
    return com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.isNoneMarker(
        normalized);
  }

  private int countMethodHeadings(final String document) {
    if (document == null || document.isBlank()) {
      return 0;
    }
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(document);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private boolean hasRequiredExternalMetadata(final String document) {
    final String section = extractTopLevelSectionBody(document, 2);
    if (section.isBlank()) {
      return false;
    }
    final List<String> missingJa =
        collectMissingMetadataLabels(section, REQUIRED_EXTERNAL_SPEC_LABELS_JA);
    final List<String> missingEn =
        collectMissingMetadataLabels(section, REQUIRED_EXTERNAL_SPEC_LABELS_EN);
    return missingJa.isEmpty() || missingEn.isEmpty();
  }

  private List<String> collectMissingMetadataLabels(
      final String section, final List<String> labels) {
    final List<String> missing = new ArrayList<>();
    if (section == null || section.isBlank() || labels == null || labels.isEmpty()) {
      return missing;
    }
    for (final String label : labels) {
      if (!containsMetadataLabel(section, label)) {
        missing.add(label);
      }
    }
    return missing;
  }

  private boolean containsMetadataLabel(final String section, final String label) {
    if (section == null || section.isBlank() || label == null || label.isBlank()) {
      return false;
    }
    final Pattern pattern =
        Pattern.compile("(?m)^\\s*-\\s*" + Pattern.quote(label) + "\\s*[:：]\\s*.+$");
    return pattern.matcher(section).find();
  }

  private boolean hasUnsupportedNoArgPreconditionContent(
      final String document, final List<MethodInfo> methods) {
    final Map<String, Deque<String>> methodBlocks = indexMethodBlocksByHeading(document);
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(methods)) {
      if (isUnsupportedNoArgPreconditionContent(method, methodBlocks)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasMissingSourceBackedPreconditions(
      final String document, final List<MethodInfo> methods) {
    final Map<String, Deque<String>> methodBlocks = indexMethodBlocksByHeading(document);
    for (final MethodInfo method : PromptInputCanonicalizer.sortMethods(methods)) {
      if (isMissingSourceBackedPrecondition(method, methodBlocks)) {
        return true;
      }
    }
    return false;
  }

  private boolean isUnsupportedNoArgPreconditionContent(
      final MethodInfo method, final Map<String, Deque<String>> methodBlocks) {
    if (method == null || method.getParameterCount() > 0) {
      return false;
    }
    final List<String> sourceBacked =
        fallbackPreconditionExtractor.collectSourceBackedPreconditions(method);
    if (!sourceBacked.isEmpty()) {
      return false;
    }
    final MethodSubsections subsections = pollMethodSubsections(methodBlocks, method);
    return containsMeaningfulLines(subsections.preconditions());
  }

  private boolean isMissingSourceBackedPrecondition(
      final MethodInfo method, final Map<String, Deque<String>> methodBlocks) {
    if (method == null) {
      return false;
    }
    final List<String> sourceBacked =
        fallbackPreconditionExtractor.collectSourceBackedPreconditions(method);
    if (sourceBacked.isEmpty()) {
      return false;
    }
    final Deque<String> blocks = pollMethodBlocks(methodBlocks, method);
    if (blocks.isEmpty()) {
      return true;
    }
    final MethodSubsections subsections = extractMethodSubsections(blocks.pollFirst());
    return hasMissingConditions(subsections.preconditions(), sourceBacked);
  }

  private MethodSubsections pollMethodSubsections(
      final Map<String, Deque<String>> methodBlocks, final MethodInfo method) {
    final Deque<String> blocks = pollMethodBlocks(methodBlocks, method);
    if (blocks.isEmpty()) {
      return MethodSubsections.empty();
    }
    return extractMethodSubsections(blocks.pollFirst());
  }

  private Deque<String> pollMethodBlocks(
      final Map<String, Deque<String>> methodBlocks, final MethodInfo method) {
    if (methodBlocks == null || methodBlocks.isEmpty() || method == null) {
      return new ArrayDeque<>();
    }
    final String methodName =
        com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
            .normalizeMethodName(resolveMethodDisplayName(method));
    final Deque<String> blocks = methodBlocks.get(methodName);
    return blocks == null ? new ArrayDeque<>() : blocks;
  }

  private boolean hasMissingConditions(
      final String section, final List<String> sourceBackedConditions) {
    if (sourceBackedConditions == null || sourceBackedConditions.isEmpty()) {
      return false;
    }
    for (final String condition : sourceBackedConditions) {
      if (!containsConditionLine(section, condition)) {
        return true;
      }
    }
    return false;
  }

  private String buildRepairedDocument(
      final String document,
      final ClassInfo classInfo,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final boolean hybrid) {
    final boolean japanese = isJapaneseLocale();
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String title = findTitleLine(document, className, japanese);
    String purposeBody = extractTopLevelSectionBody(document, 1);
    if (purposeBody.isBlank()) {
      purposeBody = defaultPurposeSectionBody(className, japanese);
    }
    final String externalBody = buildExternalSpecificationBody(classInfo, japanese);
    final String methodsBody =
        buildCanonicalMethodsBody(document, classInfo, promptContext, japanese, hybrid);
    final String cautionsBody =
        ensureSectionContent(extractTopLevelSectionBody(document, 4), japanese ? "- なし" : "- None");
    final String recommendationsBody =
        ensureSectionContent(extractTopLevelSectionBody(document, 5), japanese ? "- なし" : "- None");
    final String openQuestionsBody =
        buildOpenQuestionsSectionBody(promptContext, japanese, document);
    final StringBuilder sb = new StringBuilder();
    sb.append(title).append("\n\n");
    sb.append(japanese ? "## 1. 目的と責務（事実）" : "## 1. Purpose and Responsibilities (Facts)")
        .append("\n");
    sb.append(purposeBody.strip()).append("\n\n");
    sb.append(japanese ? "## 2. クラス外部仕様" : "## 2. External Class Specification").append("\n");
    sb.append(externalBody.strip()).append("\n\n");
    sb.append(japanese ? "## 3. メソッド仕様" : "## 3. Method Specifications").append("\n\n");
    if (methodsBody.isBlank()) {
      sb.append("- ").append(japanese ? "なし" : "None").append("\n\n");
    } else {
      sb.append(methodsBody.strip()).append("\n\n");
    }
    sb.append(japanese ? "## 4. 要注意事項" : "## 4. Cautions").append("\n");
    sb.append(cautionsBody.strip()).append("\n\n");
    sb.append(japanese ? "## 5. 改善提案（任意）" : "## 5. Recommendations (Optional)").append("\n");
    sb.append(recommendationsBody.strip()).append("\n\n");
    sb.append(japanese ? "## 6. 未確定事項（解析情報不足）" : "## 6. Open Questions (Insufficient Analysis)")
        .append("\n");
    sb.append(openQuestionsBody.strip()).append("\n");
    return sb.toString().strip();
  }

  private String findTitleLine(
      final String document, final String className, final boolean japanese) {
    if (document != null) {
      for (final String line : document.split("\\R")) {
        final String trimmed = line == null ? "" : line.strip();
        if (trimmed.startsWith("# ")) {
          return trimmed;
        }
      }
    }
    return japanese ? "# " + className + " 詳細設計" : "# " + className + " Detailed Design";
  }

  private String defaultPurposeSectionBody(final String className, final boolean japanese) {
    return japanese
        ? "- `" + className + "` の外部契約とメソッド挙動を解析結果に基づいて整理する。"
        : "- Organize the external contract and method behavior of `"
            + className
            + "` based on analysis results.";
  }

  private String buildExternalSpecificationBody(final ClassInfo classInfo, final boolean japanese) {
    final String className = DocumentUtils.getSimpleName(classInfo.getFqn());
    final String packageName =
        DocumentUtils.formatPackageNameForDisplay(DocumentUtils.getPackageName(classInfo));
    final String filePath = nullSafe(classInfo.getFilePath());
    final String classType = DocumentUtils.buildClassType(classInfo);
    final String extendsInfo = formatExtendsInfo(classInfo);
    final String implementsInfo = formatImplementsInfo(classInfo);
    final String classAttributes = buildClassAttributes(classInfo);
    final String fieldsInfo = DocumentUtils.buildFieldsInfo(classInfo.getFields());
    final StringBuilder sb = new StringBuilder();
    if (japanese) {
      sb.append("- クラス名: `").append(className).append("`\n");
      sb.append("- パッケージ: `").append(packageName).append("`\n");
      sb.append("- ファイルパス: `").append(filePath).append("`\n");
      sb.append("- クラス種別: ").append(classType).append("\n");
      sb.append("- 継承: ").append(extendsInfo).append("\n");
      sb.append("- 実装インターフェース: ").append(implementsInfo).append("\n");
      sb.append("- クラス属性:\n");
      appendIndentedLines(sb, classAttributes, "なし");
      sb.append("- フィールド一覧:\n");
      appendIndentedLines(sb, fieldsInfo, "フィールドなし");
      return sb.toString().strip();
    }
    sb.append("- Class Name: `").append(className).append("`\n");
    sb.append("- Package: `").append(packageName).append("`\n");
    sb.append("- File Path: `").append(filePath).append("`\n");
    sb.append("- Class Type: ").append(classType).append("\n");
    sb.append("- Extends: ").append(extendsInfo).append("\n");
    sb.append("- Implements: ").append(implementsInfo).append("\n");
    sb.append("- Class Attributes:\n");
    appendIndentedLines(sb, classAttributes, "None");
    sb.append("- Fields:\n");
    appendIndentedLines(sb, fieldsInfo, "None");
    return sb.toString().strip();
  }

  private void appendIndentedLines(
      final StringBuilder sb, final String value, final String fallbackLine) {
    if (sb == null) {
      return;
    }
    if (value == null || value.isBlank()) {
      sb.append("  - ").append(fallbackLine).append("\n");
      return;
    }
    int emitted = 0;
    for (final String line : value.split("\\R")) {
      if (line == null || line.isBlank()) {
        continue;
      }
      final String normalized = line.strip();
      if (normalized.startsWith("-")) {
        sb.append("  ").append(normalized).append("\n");
      } else {
        sb.append("  - ").append(normalized).append("\n");
      }
      emitted++;
    }
    if (emitted == 0) {
      sb.append("  - ").append(fallbackLine).append("\n");
    }
  }

  private String buildCanonicalMethodsBody(
      final String document,
      final ClassInfo classInfo,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final boolean japanese,
      final boolean hybrid) {
    final Map<String, Deque<String>> blocksByMethod = indexMethodBlocksByHeading(document);
    final List<MethodInfo> methods =
        PromptInputCanonicalizer.sortMethods(promptContext.specMethods());
    if (methods.isEmpty()) {
      return "";
    }
    final String classSimpleName = DocumentUtils.getSimpleName(classInfo.getFqn());
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < methods.size(); i++) {
      final MethodInfo method = methods.get(i);
      final String methodName = resolveMethodDisplayName(method);
      final boolean isTemplateTarget =
          hybrid && methodDocClassifier.isTemplateTarget(method, classSimpleName);
      if (isTemplateTarget) {
        // Use deterministic template rendering for simple methods
        sb.append(templateMethodRenderer.render(method, i + 1, japanese)).append("\n\n");
      } else {
        // Use LLM-generated block (existing logic)
        final String normalizedMethodName =
            com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
                .normalizeMethodName(methodName);
        final Set<String> uncertainMethodNames =
            promptContext.validationFacts().uncertainDynamicMethodNamesFor(normalizedMethodName);
        String block = "";
        final Deque<String> candidates = blocksByMethod.get(normalizedMethodName);
        if (candidates != null && !candidates.isEmpty()) {
          block = candidates.pollFirst();
        }
        final MethodSubsections subsections =
            sanitizeMethodSubsections(
                method,
                extractMethodSubsections(block),
                classSimpleName,
                uncertainMethodNames,
                promptContext.validationFacts(),
                japanese);
        sb.append(buildMethodBlock(i + 1, methodName, subsections, japanese));
      }
    }
    return sb.toString().strip();
  }

  private Map<String, Deque<String>> indexMethodBlocksByHeading(final String document) {
    final Map<String, Deque<String>> indexed = new LinkedHashMap<>();
    if (document == null || document.isBlank()) {
      return indexed;
    }
    final StringBuilder currentBlock = new StringBuilder();
    boolean collecting = false;
    for (final String line : document.split("\\R", -1)) {
      final String stripped = line == null ? "" : line.strip();
      if (isMethodHeadingLine(stripped)) {
        if (collecting) {
          addMethodBlock(indexed, currentBlock.toString());
        }
        currentBlock.setLength(0);
        currentBlock.append(line).append("\n");
        collecting = true;
      } else if (collecting && isMethodBlockBoundary(stripped)) {
        addMethodBlock(indexed, currentBlock.toString());
        currentBlock.setLength(0);
        collecting = false;
      } else if (collecting) {
        currentBlock.append(line).append("\n");
      }
    }
    if (collecting) {
      addMethodBlock(indexed, currentBlock.toString());
    }
    return indexed;
  }

  private boolean isMethodHeadingLine(final String line) {
    return line != null && METHOD_HEADING_PATTERN.matcher(line).matches();
  }

  private boolean isMethodBlockBoundary(final String line) {
    return line != null && SECTION_FOUR_HEADING_PATTERN.matcher(line).matches();
  }

  private void addMethodBlock(final Map<String, Deque<String>> indexed, final String block) {
    if (indexed == null || block == null || block.isBlank()) {
      return;
    }
    final String headingName = extractMethodHeadingName(block);
    if (!headingName.isBlank()) {
      indexed
          .computeIfAbsent(
              com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
                  .normalizeMethodName(headingName),
              ignored -> new ArrayDeque<>())
          .add(block);
    }
  }

  private String extractMethodHeadingName(final String methodBlock) {
    if (methodBlock == null || methodBlock.isBlank()) {
      return "";
    }
    final Matcher matcher = METHOD_HEADING_PATTERN.matcher(methodBlock);
    if (!matcher.find()) {
      return "";
    }
    return matcher.group(1).strip();
  }

  private MethodSubsections extractMethodSubsections(final String methodBlock) {
    if (methodBlock == null || methodBlock.isBlank()) {
      return MethodSubsections.empty();
    }
    final Map<Integer, StringBuilder> byOrder = new LinkedHashMap<>();
    int currentOrder = 0;
    for (final String line : methodBlock.split("\\R", -1)) {
      final Matcher matcher = SUBSECTION_HEADING_PATTERN.matcher(line.strip());
      if (matcher.matches()) {
        currentOrder = Integer.parseInt(matcher.group(1));
        byOrder.computeIfAbsent(currentOrder, ignored -> new StringBuilder());
      } else if (currentOrder > 0) {
        byOrder.get(currentOrder).append(line).append("\n");
      }
    }
    return new MethodSubsections(
        sectionValue(byOrder, 1),
        sectionValue(byOrder, 2),
        sectionValue(byOrder, 3),
        sectionValue(byOrder, 4),
        sectionValue(byOrder, 5),
        sectionValue(byOrder, 6),
        sectionValue(byOrder, 7));
  }

  private String sectionValue(final Map<Integer, StringBuilder> byOrder, final int order) {
    if (byOrder == null || !byOrder.containsKey(order)) {
      return "";
    }
    return byOrder.get(order).toString().strip();
  }

  private MethodSubsections sanitizeMethodSubsections(
      final MethodInfo method,
      final MethodSubsections subsections,
      final String classSimpleName,
      final Set<String> uncertainMethodNames,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts validationFacts,
      final boolean japanese) {
    final List<String> sourceBackedPreconditions =
        fallbackPreconditionExtractor.collectSourceBackedPreconditions(method);
    final String noneLine = japanese ? "なし" : "None";
    String inputOutput =
        ensureSectionContent(
            filterUncertainMethodLines(subsections.inputs(), uncertainMethodNames),
            buildDefaultInputOutput(method, japanese));
    inputOutput = normalizeInputOutputSection(inputOutput, japanese);
    String preconditions =
        sanitizeNoArgPreconditions(
            filterUncertainMethodLines(subsections.preconditions(), uncertainMethodNames),
            method,
            sourceBackedPreconditions,
            japanese);
    preconditions =
        ensureSourceBackedPreconditions(preconditions, sourceBackedPreconditions, japanese);
    preconditions = ensureSectionContent(preconditions, noneLine);
    String postconditions =
        normalizeNoAnalysisPlaceholder(
            filterUncertainMethodLines(subsections.postconditions(), uncertainMethodNames));
    postconditions =
        enrichOrFallback(
            postconditions,
            buildDefaultPostconditions(method, japanese),
            () ->
                fallbackPathSectionBuilder.collectFallbackPostconditions(
                    method, japanese, validationFacts));
    String normalFlow = filterUncertainMethodLines(subsections.normalFlow(), uncertainMethodNames);
    if (isConstructorMethod(method, classSimpleName) && containsFailureLikeFlow(normalFlow)) {
      normalFlow = "";
    }
    normalFlow =
        enrichOrFallback(
            normalFlow,
            buildDefaultNormalFlow(method, japanese),
            () ->
                fallbackPathSectionBuilder.collectFallbackNormalFlows(
                    method,
                    calledMethodFilter.filterCalledMethodsForSpecificationWithArgumentLiterals(
                        method, validationFacts),
                    japanese,
                    validationFacts));
    final String errorBoundaries =
        enrichOrFallback(
            filterUncertainMethodLines(subsections.errorBoundaries(), uncertainMethodNames),
            buildDefaultErrorBoundary(method, japanese),
            () ->
                fallbackPathSectionBuilder.collectFallbackErrorBoundaries(
                    method, japanese, validationFacts));
    final String dependencies =
        ensureSectionContent(
            filterUncertainMethodLines(subsections.dependencies(), uncertainMethodNames), noneLine);
    String testViewpoints =
        ensureSectionContent(
            filterUncertainMethodLines(subsections.testViewpoints(), uncertainMethodNames),
            buildDefaultTestViewpoints(japanese));
    testViewpoints =
        ensureTestViewpointPathCoverage(
            testViewpoints,
            postconditions,
            normalFlow,
            errorBoundaries,
            method,
            japanese,
            validationFacts);
    return new MethodSubsections(
        inputOutput,
        preconditions,
        postconditions,
        normalFlow,
        errorBoundaries,
        dependencies,
        testViewpoints);
  }

  private String sanitizeNoArgPreconditions(
      final String preconditions,
      final MethodInfo method,
      final List<String> sourceBackedPreconditions,
      final boolean japanese) {
    if (method == null || method.getParameterCount() > 0 || !sourceBackedPreconditions.isEmpty()) {
      return preconditions;
    }
    if (!containsMeaningfulLines(preconditions)) {
      return preconditions;
    }
    return japanese ? "なし" : "None";
  }

  private boolean containsMeaningfulLines(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized = normalizeLineWithoutLeadingBullet(rawLine);
      if (normalized.isBlank()
          || com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.isNoneMarker(
              normalized)) {
        continue;
      }
      return true;
    }
    return false;
  }

  private String ensureSourceBackedPreconditions(
      final String preconditions,
      final List<String> sourceBackedPreconditions,
      final boolean japanese) {
    if (sourceBackedPreconditions == null || sourceBackedPreconditions.isEmpty()) {
      return preconditions;
    }
    final List<String> lines = collectPresentPreconditionLines(preconditions);
    appendMissingSourceBackedConditions(lines, sourceBackedPreconditions);
    if (lines.isEmpty()) {
      return "- " + (japanese ? "なし" : "None");
    }
    return String.join("\n", lines);
  }

  private List<String> collectPresentPreconditionLines(final String preconditions) {
    if (preconditions == null || preconditions.isBlank()) {
      return new ArrayList<>();
    }
    final List<String> lines = new ArrayList<>();
    for (final String line : preconditions.split("\\R")) {
      if (isPresentPreconditionLine(line)) {
        lines.add(line.strip());
      }
    }
    return lines;
  }

  private boolean isPresentPreconditionLine(final String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    final String normalized = normalizeLineWithoutLeadingBullet(line);
    return !normalized.isBlank()
        && !com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.isNoneMarker(
            normalized);
  }

  private void appendMissingSourceBackedConditions(
      final List<String> lines, final List<String> sourceBackedPreconditions) {
    if (lines == null || sourceBackedPreconditions == null || sourceBackedPreconditions.isEmpty()) {
      return;
    }
    for (final String condition : sourceBackedPreconditions) {
      if (shouldAppendSourceBackedCondition(lines, condition)) {
        lines.add("- " + condition.strip());
      }
    }
  }

  private boolean shouldAppendSourceBackedCondition(
      final List<String> lines, final String condition) {
    if (condition == null || condition.isBlank()) {
      return false;
    }
    return !containsConditionInLines(lines, condition);
  }

  private boolean containsConditionInLines(final List<String> lines, final String condition) {
    if (lines == null || lines.isEmpty() || condition == null || condition.isBlank()) {
      return false;
    }
    final String expected = normalizeMatchToken(condition);
    if (expected.isBlank()) {
      return false;
    }
    for (final String line : lines) {
      final String normalized = normalizeMatchToken(stripLeadingBulletMarker(line));
      if (normalized.contains(expected)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsConditionLine(final String section, final String condition) {
    if (section == null || section.isBlank() || condition == null || condition.isBlank()) {
      return false;
    }
    final String expected = normalizeMatchToken(condition);
    if (expected.isBlank()) {
      return false;
    }
    for (final String line : section.split("\\R")) {
      final String normalized = normalizeMatchToken(stripLeadingBulletMarker(line));
      if (normalized.contains(expected)) {
        return true;
      }
    }
    return false;
  }

  private String normalizeMatchToken(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value
        .replace("`", "")
        .replace("\"", "")
        .replace("'", "")
        .replace("。", "")
        .replace("、", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", "");
  }

  private String stripLeadingBulletMarker(final String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.replaceFirst(LEADING_BULLET_PATTERN, "");
  }

  private String normalizeLineWithoutLeadingBullet(final String line) {
    return com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.normalizeLine(
        stripLeadingBulletMarker(line));
  }

  private String filterUncertainMethodLines(
      final String section, final Set<String> uncertainMethodNames) {
    if (section == null || section.isBlank()) {
      return section == null ? "" : section;
    }
    if (uncertainMethodNames == null || uncertainMethodNames.isEmpty()) {
      return listStructureNormalizer.normalizeOrderedListLines(section);
    }
    final List<String> retained = new ArrayList<>();
    for (final String line : section.split("\\R")) {
      if (isRetainedMethodLine(line, uncertainMethodNames)) {
        retained.add(line.stripTrailing());
      }
    }
    return listStructureNormalizer.normalizeOrderedListLines(String.join("\n", retained));
  }

  private boolean isRetainedMethodLine(final String line, final Set<String> uncertainMethodNames) {
    return line != null
        && !line.isBlank()
        && !containsUncertainMethodToken(line, uncertainMethodNames);
  }

  private boolean containsUncertainMethodToken(
      final String line, final Set<String> uncertainMethodNames) {
    if (line == null
        || line.isBlank()
        || uncertainMethodNames == null
        || uncertainMethodNames.isEmpty()) {
      return false;
    }
    for (final String methodName : uncertainMethodNames) {
      if (methodName != null
          && !methodName.isBlank()
          && com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
              .containsMethodToken(line, methodName)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFailureLikeFlow(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    final String normalized =
        com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.normalizeLine(
            section);
    return normalized.contains("early-return")
        || normalized.contains("early return")
        || normalized.contains("failure")
        || normalized.contains("error")
        || normalized.contains("boundary")
        || normalized.contains("失敗")
        || normalized.contains("異常")
        || normalized.contains("境界");
  }

  private String ensureSectionContent(final String value, final String fallbackLine) {
    if (value == null || value.isBlank()) {
      return fallbackLine == null ? "" : fallbackLine.strip();
    }
    return value.strip();
  }

  private String enrichOrFallback(
      final String value,
      final String fallbackLine,
      final java.util.function.Supplier<List<String>> enricher) {
    if (value != null && !value.isBlank() && !isNoAnalysisOnlySection(value)) {
      return value.strip();
    }
    final List<String> enriched = enricher.get();
    if (enriched != null && !enriched.isEmpty()) {
      return String.join("\n", enriched).strip();
    }
    return fallbackLine == null ? "" : fallbackLine.strip();
  }

  private String normalizeNoAnalysisPlaceholder(final String section) {
    if (section == null || section.isBlank()) {
      return "";
    }
    if (isNoAnalysisOnlySection(section)) {
      return "";
    }
    return section.strip();
  }

  private boolean isNoAnalysisOnlySection(final String section) {
    if (section == null || section.isBlank()) {
      return false;
    }
    for (final String rawLine : section.split("\\R")) {
      final String normalized = normalizeLineWithoutLeadingBullet(rawLine);
      final boolean ignorable =
          normalized.isBlank()
              || com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils
                  .isNoneMarker(normalized)
              || com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapLexicon
                  .isAnalysisGapLine(rawLine);
      if (!ignorable) {
        return false;
      }
    }
    return true;
  }

  private String bulletize(final String value) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    final String normalized = value.strip();
    if (normalized.startsWith("-")) {
      return normalized;
    }
    return "- " + normalized;
  }

  private String buildMethodBlock(
      final int sectionNo,
      final String methodName,
      final MethodSubsections sections,
      final boolean japanese) {
    final StringBuilder sb = new StringBuilder();
    sb.append("### 3.").append(sectionNo).append(" ").append(methodName).append("\n");
    appendMethodSubsection(sb, sectionNo, 1, subsectionLabel(1, japanese), sections.inputs());
    appendMethodSubsection(
        sb, sectionNo, 2, subsectionLabel(2, japanese), sections.preconditions());
    appendMethodSubsection(
        sb, sectionNo, 3, subsectionLabel(3, japanese), sections.postconditions());
    appendMethodSubsection(sb, sectionNo, 4, subsectionLabel(4, japanese), sections.normalFlow());
    appendMethodSubsection(
        sb, sectionNo, 5, subsectionLabel(5, japanese), sections.errorBoundaries());
    appendMethodSubsection(sb, sectionNo, 6, subsectionLabel(6, japanese), sections.dependencies());
    appendMethodSubsection(
        sb, sectionNo, 7, subsectionLabel(7, japanese), sections.testViewpoints());
    return sb.toString();
  }

  private void appendMethodSubsection(
      final StringBuilder sb,
      final int sectionNo,
      final int subsectionNo,
      final String label,
      final String content) {
    sb.append("#### 3.")
        .append(sectionNo)
        .append(".")
        .append(subsectionNo)
        .append(" ")
        .append(label)
        .append("\n");
    if (content == null || content.isBlank()) {
      sb.append("-\n\n");
    } else {
      final String stripped = content.strip();
      if (startsWithListMarker(stripped)) {
        sb.append(stripped).append("\n\n");
      } else {
        sb.append("- ").append(stripped).append("\n\n");
      }
    }
  }

  private boolean startsWithListMarker(final String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    if (value.startsWith("-")) {
      return true;
    }
    return value.matches("^\\d+\\.\\s+.*");
  }

  private String subsectionLabel(final int subsectionNo, final boolean japanese) {
    if (japanese) {
      return switch (subsectionNo) {
        case 1 -> "入出力";
        case 2 -> "事前条件";
        case 3 -> "事後条件";
        case 4 -> "正常フロー";
        case 5 -> "異常・境界";
        case 6 -> "依存呼び出し";
        case 7 -> "テスト観点";
        default -> "";
      };
    }
    return switch (subsectionNo) {
      case 1 -> "Inputs/Outputs";
      case 2 -> "Preconditions";
      case 3 -> "Postconditions";
      case 4 -> "Normal Flow";
      case 5 -> "Error/Boundary Handling";
      case 6 -> "Dependencies";
      case 7 -> "Test Viewpoints";
      default -> "";
    };
  }

  private String buildDefaultInputOutput(final MethodInfo method, final boolean japanese) {
    final String signature =
        method == null ? msg(DOCUMENT_VALUE_NA) : nullSafe(method.getSignature());
    return japanese ? "- 入力/出力: `" + signature + "`" : "- Inputs/Outputs: `" + signature + "`";
  }

  private String buildDefaultPostconditions(final MethodInfo method, final boolean japanese) {
    if (constructorSemantics.isTrivialEmptyConstructor(method)) {
      return "- "
          + msg(
              japanese
                  ? "document.llm.fallback.path.postcondition.empty_constructor.ja"
                  : "document.llm.fallback.path.postcondition.empty_constructor.en");
    }
    final String signature =
        method == null ? msg(DOCUMENT_VALUE_NA) : nullSafe(method.getSignature());
    return japanese
        ? "- 戻り値の詳細はシグネチャに準拠する: `" + signature + "`"
        : "- Return value details follow the signature: `" + signature + "`";
  }

  private String buildDefaultNormalFlow(final MethodInfo method, final boolean japanese) {
    if (constructorSemantics.isTrivialEmptyConstructor(method)) {
      return "- "
          + msg(
              japanese
                  ? "document.llm.fallback.path.normal.empty_constructor.ja"
                  : "document.llm.fallback.path.normal.empty_constructor.en");
    }
    if (hasReturnStatement(method)) {
      return japanese
          ? "- ソースコードに記載された処理を実行し、`return`文で結果を返す。"
          : "- Execute source-defined logic and return the result according to `return` statements.";
    }
    return japanese ? "- ソースコードに記載された処理を順に実行する。" : "- Execute source-defined logic in order.";
  }

  private String buildDefaultErrorBoundary(final MethodInfo method, final boolean japanese) {
    if (hasExplicitThrowStatement(method)) {
      return japanese
          ? "- ソースコード上で例外が送出される経路を検証する。"
          : "- Verify paths where exceptions are explicitly thrown in source.";
    }
    return japanese
        ? "- ソースコード上に明示的な例外送出・境界分岐は確認されない。"
        : "- No explicit exception throw or boundary-only branch is observed in source.";
  }

  private String buildDefaultTestViewpoints(final boolean japanese) {
    return japanese
        ? "- 分岐・例外・境界値を優先してテストする。"
        : "- Prioritize branch, exception, and boundary-value test cases.";
  }

  private String buildOpenQuestionsSectionBody(
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final boolean japanese,
      final String sourceDocument) {
    final LinkedHashSet<String> openQuestions =
        collectOpenQuestions(promptContext, sourceDocument, japanese);
    final List<String> normalized = normalizeOpenQuestions(openQuestions);
    if (normalized.isEmpty()) {
      return bulletize(japanese ? "なし" : "None");
    }
    return String.join("\n", normalized);
  }

  private LinkedHashSet<String> collectOpenQuestions(
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final String sourceDocument,
      final boolean japanese) {
    final LinkedHashSet<String> openQuestions = new LinkedHashSet<>();
    openQuestions.addAll(collectDynamicOpenQuestions(promptContext, japanese));
    openQuestions.addAll(collectAnalysisGapOpenQuestions(sourceDocument, japanese));
    return openQuestions;
  }

  private List<String> collectDynamicOpenQuestions(
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmPromptContext promptContext,
      final boolean japanese) {
    if (promptContext == null || promptContext.specMethods().isEmpty()) {
      return List.of();
    }
    final List<String> dynamicOpenQuestions =
        openQuestionBuilder.buildFallbackOpenQuestions(
            promptContext.validationFacts().methodNames(),
            promptContext.validationFacts().uncertainDynamicMethodDisplayNames(),
            List.of(),
            japanese);
    if (dynamicOpenQuestions == null || dynamicOpenQuestions.isEmpty()) {
      return List.of();
    }
    final List<String> normalized = new ArrayList<>();
    for (final String openQuestion : dynamicOpenQuestions) {
      if (openQuestion != null && !openQuestion.isBlank()) {
        normalized.add(openQuestion.strip());
      }
    }
    return normalized;
  }

  private List<String> normalizeOpenQuestions(final Set<String> openQuestions) {
    if (openQuestions == null || openQuestions.isEmpty()) {
      return List.of();
    }
    final List<String> normalized = new ArrayList<>();
    for (final String openQuestion : openQuestions) {
      if (openQuestion != null && !openQuestion.isBlank()) {
        normalized.add(bulletize(openQuestion));
      }
    }
    return normalized;
  }

  private List<String> collectAnalysisGapOpenQuestions(
      final String sourceDocument, final boolean japanese) {
    final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmAnalysisGapInspector
            .AnalysisGapInspection
        inspection = analysisGapInspector.inspectMainSections(sourceDocument);
    if (!inspection.hasGap()) {
      return List.of();
    }
    final LinkedHashSet<String> questions = new LinkedHashSet<>();
    for (final String methodName : inspection.methodNames()) {
      if (methodName == null || methodName.isBlank()) {
        continue;
      }
      questions.add(
          msg(
              japanese
                  ? "document.llm.open_question.analysis_gap_method.ja"
                  : "document.llm.open_question.analysis_gap_method.en",
              methodName));
    }
    if (questions.isEmpty()) {
      questions.add(
          msg(
              japanese
                  ? "document.llm.open_question.analysis_gap_general.ja"
                  : "document.llm.open_question.analysis_gap_general.en"));
    }
    return new ArrayList<>(questions);
  }

  private String normalizeInputOutputSection(final String inputOutput, final boolean japanese) {
    if (inputOutput == null || inputOutput.isBlank()) {
      return inputOutput == null ? "" : inputOutput;
    }
    final List<String> normalized = new ArrayList<>();
    for (final String rawLine : inputOutput.split("\\R")) {
      final String normalizedLine = normalizeInputOutputLine(rawLine, japanese);
      if (!normalizedLine.isBlank()) {
        normalized.add(normalizedLine);
      }
    }
    if (normalized.isEmpty()) {
      return japanese ? "なし" : "None";
    }
    return String.join("\n", normalized).strip();
  }

  private String normalizeInputOutputLine(final String rawLine, final boolean japanese) {
    if (rawLine == null || rawLine.isBlank()) {
      return "";
    }
    final String stripped = rawLine.strip();
    final Matcher matcher = MALFORMED_INLINE_INPUT_NONE_PATTERN.matcher(stripped);
    if (matcher.matches()) {
      return "- "
          + matcher.group(1).strip()
          + ": "
          + resolveInlineNoneValue(matcher.group(2), japanese);
    }
    return rawLine.stripTrailing();
  }

  private String resolveInlineNoneValue(final String noneToken, final boolean japanese) {
    if (noneToken == null || noneToken.isBlank()) {
      return japanese ? "なし" : "None";
    }
    final String normalized = noneToken.strip();
    if ("none".equalsIgnoreCase(normalized)) {
      return "None";
    }
    if ("なし".equals(normalized)) {
      return "なし";
    }
    return japanese ? "なし" : "None";
  }

  private boolean hasReturnStatement(final MethodInfo method) {
    if (method == null || method.getSourceCode() == null || method.getSourceCode().isBlank()) {
      return false;
    }
    return Pattern.compile("(?m)\\breturn\\b").matcher(method.getSourceCode()).find();
  }

  private boolean hasExplicitThrowStatement(final MethodInfo method) {
    if (method == null || method.getSourceCode() == null || method.getSourceCode().isBlank()) {
      return false;
    }
    return Pattern.compile("(?m)\\bthrow\\b").matcher(method.getSourceCode()).find();
  }

  private String ensureTestViewpointPathCoverage(
      final String testViewpoints,
      final String postconditions,
      final String normalFlow,
      final String errorBoundaries,
      final MethodInfo method,
      final boolean japanese,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts
          validationFacts) {
    final Set<String> expectedPathIds =
        collectExpectedPathIds(postconditions, normalFlow, errorBoundaries);
    if (expectedPathIds.isEmpty()) {
      return testViewpoints;
    }
    final List<String> lines = new ArrayList<>();
    final Set<String> seen = new LinkedHashSet<>();
    addExistingTestViewpointLines(testViewpoints, lines, seen);
    final Set<String> missing = collectMissingPathIds(expectedPathIds, lines);
    if (!missing.isEmpty()) {
      final Map<String, PathViewpointFact> expectedPathFacts =
          collectPathViewpointFacts(postconditions, normalFlow, errorBoundaries);
      mergeFallbackViewpointsForMissingPaths(
          method, japanese, validationFacts, lines, seen, missing);
      appendSupplementalViewpointsForMissingPaths(
          missing, expectedPathFacts, japanese, lines, seen);
    }
    if (lines.isEmpty()) {
      return buildDefaultTestViewpoints(japanese);
    }
    return String.join("\n", lines).strip();
  }

  private Set<String> collectExpectedPathIds(
      final String postconditions, final String normalFlow, final String errorBoundaries) {
    final Set<String> expectedPathIds = new LinkedHashSet<>();
    expectedPathIds.addAll(extractPathIds(postconditions));
    expectedPathIds.addAll(extractPathIds(normalFlow));
    expectedPathIds.addAll(extractPathIds(errorBoundaries));
    return expectedPathIds;
  }

  private void addExistingTestViewpointLines(
      final String testViewpoints, final List<String> lines, final Set<String> seen) {
    for (final String line : splitNonBlankLines(testViewpoints)) {
      addUniqueLine(lines, seen, line);
    }
  }

  private Set<String> collectMissingPathIds(
      final Set<String> expectedPathIds, final List<String> lines) {
    final Set<String> currentPathIds = extractPathIds(String.join("\n", lines));
    final Set<String> missing = new LinkedHashSet<>(expectedPathIds);
    missing.removeAll(currentPathIds);
    return missing;
  }

  private void mergeFallbackViewpointsForMissingPaths(
      final MethodInfo method,
      final boolean japanese,
      final com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmValidationFacts validationFacts,
      final List<String> lines,
      final Set<String> seen,
      final Set<String> missing) {
    final List<String> fallbackViewpoints =
        fallbackPathSectionBuilder.collectFallbackTestViewpoints(method, japanese, validationFacts);
    for (final String fallbackViewpoint : fallbackViewpoints) {
      final Set<String> intersection = intersectWithMissingPathIds(fallbackViewpoint, missing);
      if (!intersection.isEmpty()) {
        addUniqueLine(lines, seen, bulletize(fallbackViewpoint));
        missing.removeAll(intersection);
      }
    }
  }

  private Set<String> intersectWithMissingPathIds(
      final String viewpointLine, final Set<String> missingPathIds) {
    final Set<String> fallbackPathIds = extractPathIds(viewpointLine);
    final Set<String> intersection = new LinkedHashSet<>(fallbackPathIds);
    intersection.retainAll(missingPathIds);
    return intersection;
  }

  private void appendSupplementalViewpointsForMissingPaths(
      final Set<String> missing,
      final Map<String, PathViewpointFact> expectedPathFacts,
      final boolean japanese,
      final List<String> lines,
      final Set<String> seen) {
    if (missing == null || missing.isEmpty()) {
      return;
    }
    for (final String pathId : missing) {
      final String supplemental =
          buildSupplementalTestViewpointLine(pathId, expectedPathFacts.get(pathId), japanese);
      addUniqueLine(lines, seen, bulletize(supplemental));
    }
  }

  private List<String> splitNonBlankLines(final String section) {
    if (section == null || section.isBlank()) {
      return List.of();
    }
    final List<String> lines = new ArrayList<>();
    for (final String rawLine : section.split("\\R")) {
      if (rawLine == null || rawLine.isBlank()) {
        continue;
      }
      lines.add(rawLine.stripTrailing());
    }
    return lines;
  }

  private void addUniqueLine(final List<String> lines, final Set<String> seen, final String line) {
    if (line == null || line.isBlank() || lines == null || seen == null) {
      return;
    }
    final String normalized =
        com.craftsmanbro.fulcraft.plugins.document.core.llm.LlmDocumentTextUtils.normalizeLine(
            line);
    if (normalized.isBlank() || !seen.add(normalized)) {
      return;
    }
    lines.add(line.strip());
  }

  private Set<String> extractPathIds(final String section) {
    if (section == null || section.isBlank()) {
      return Set.of();
    }
    final LinkedHashSet<String> ids = new LinkedHashSet<>();
    final Matcher matcher = PATH_ID_TOKEN_PATTERN.matcher(section);
    while (matcher.find()) {
      ids.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return ids;
  }

  private Map<String, PathViewpointFact> collectPathViewpointFacts(final String... sections) {
    final Map<String, PathViewpointFact> facts = new LinkedHashMap<>();
    if (sections == null || sections.length == 0) {
      return facts;
    }
    for (final String section : sections) {
      mergePathViewpointFacts(facts, section);
    }
    return facts;
  }

  private void mergePathViewpointFacts(
      final Map<String, PathViewpointFact> facts, final String section) {
    for (final String line : splitNonBlankLines(section)) {
      mergePathViewpointFactsFromLine(facts, line);
    }
  }

  private void mergePathViewpointFactsFromLine(
      final Map<String, PathViewpointFact> facts, final String line) {
    final Set<String> pathIds = extractPathIds(line);
    if (pathIds.isEmpty()) {
      return;
    }
    for (final String pathId : pathIds) {
      final PathViewpointFact current =
          facts.getOrDefault(pathId, new PathViewpointFact(pathId, ""));
      final String candidateLabel = extractPathLabel(line, pathId);
      final String mergedLabel =
          selectPreferredPathLabel(current.pathLabel(), candidateLabel, pathId);
      final String mergedOutcome = mergePathOutcome(current.outcome(), line);
      facts.put(pathId, new PathViewpointFact(mergedLabel, mergedOutcome));
    }
  }

  private String mergePathOutcome(final String currentOutcome, final String line) {
    if (currentOutcome == null || currentOutcome.isBlank()) {
      return extractPathOutcome(line);
    }
    return currentOutcome;
  }

  private String extractPathLabel(final String line, final String pathId) {
    if (line == null || line.isBlank() || pathId == null || pathId.isBlank()) {
      return pathId == null ? "" : pathId;
    }
    final String stripped = line.strip();
    final String backtickLabel = extractBacktickPathLabel(stripped, pathId);
    if (!backtickLabel.isBlank()) {
      return backtickLabel;
    }
    final String bracketLabel = extractBracketPathLabel(stripped, pathId);
    if (!bracketLabel.isBlank()) {
      return bracketLabel;
    }
    return pathId;
  }

  private String extractBacktickPathLabel(final String line, final String pathId) {
    final Matcher backtickMatcher = PATH_LABEL_BACKTICK_PATTERN.matcher(line);
    while (backtickMatcher.find()) {
      final String candidateId = backtickMatcher.group(1);
      if (candidateId != null && candidateId.equalsIgnoreCase(pathId)) {
        return formatPathLabel(pathId, backtickMatcher.group(2));
      }
    }
    return "";
  }

  private String extractBracketPathLabel(final String line, final String pathId) {
    final Matcher bracketMatcher = PATH_LABEL_BRACKET_PATTERN.matcher(line);
    if (!bracketMatcher.find()) {
      return "";
    }
    final String candidateId = bracketMatcher.group(1);
    if (candidateId == null || !candidateId.equalsIgnoreCase(pathId)) {
      return "";
    }
    return formatPathLabel(pathId, bracketMatcher.group(2));
  }

  private String formatPathLabel(final String pathId, final String description) {
    if (pathId == null || pathId.isBlank()) {
      return "";
    }
    if (description == null || description.isBlank()) {
      return pathId;
    }
    return pathId + ": " + description.strip();
  }

  private String selectPreferredPathLabel(
      final String currentLabel, final String candidateLabel, final String pathId) {
    final String baseLabel =
        currentLabel == null || currentLabel.isBlank() ? pathId : currentLabel.strip();
    if (candidateLabel == null || candidateLabel.isBlank()) {
      return baseLabel;
    }
    final String normalizedCandidate = candidateLabel.strip();
    if (isSpecificPathLabel(normalizedCandidate, pathId)
        && !isSpecificPathLabel(baseLabel, pathId)) {
      return normalizedCandidate;
    }
    return baseLabel;
  }

  private boolean isSpecificPathLabel(final String label, final String pathId) {
    if (label == null || label.isBlank() || pathId == null || pathId.isBlank()) {
      return false;
    }
    final String normalized = label.strip();
    if (normalized.equalsIgnoreCase(pathId)) {
      return false;
    }
    final String compact = normalized.replaceAll("\\s+", "");
    final String pathCompact = pathId.strip().replaceAll("\\s+", "");
    return !compact.equalsIgnoreCase(pathCompact + ":")
        && !compact.equalsIgnoreCase(pathCompact + "：");
  }

  private String extractPathOutcome(final String line) {
    if (line == null || line.isBlank()) {
      return "";
    }
    final String stripped = line.strip();
    final Matcher resultMatcher = PATH_OUTCOME_RESULT_PATTERN.matcher(stripped);
    if (resultMatcher.find()) {
      return normalizeOutcomeToken(resultMatcher.group(1));
    }
    final Matcher parenMatcher = PATH_OUTCOME_PAREN_PATTERN.matcher(stripped);
    if (parenMatcher.find()) {
      return normalizeOutcomeToken(parenMatcher.group(1));
    }
    final Matcher englishMatcher = PATH_OUTCOME_EN_PATTERN.matcher(stripped);
    if (englishMatcher.find()) {
      return normalizeOutcomeToken(englishMatcher.group(1));
    }
    final Matcher arrowMatcher = PATH_OUTCOME_ARROW_PATTERN.matcher(stripped);
    if (arrowMatcher.find()) {
      return normalizeOutcomeToken(arrowMatcher.group(1));
    }
    return "";
  }

  private String normalizeOutcomeToken(final String outcome) {
    if (outcome == null || outcome.isBlank()) {
      return "";
    }
    String normalized = outcome.strip();
    if (normalized.startsWith("`") && normalized.endsWith("`") && normalized.length() > 1) {
      normalized = normalized.substring(1, normalized.length() - 1).strip();
    }
    return normalized.replaceFirst("[。.]+$", "").strip();
  }

  private String buildSupplementalTestViewpointLine(
      final String pathId, final PathViewpointFact pathFact, final boolean japanese) {
    String label = pathId == null ? "" : pathId.strip();
    String outcome = "";
    if (pathFact != null) {
      if (pathFact.pathLabel() != null && !pathFact.pathLabel().isBlank()) {
        label = pathFact.pathLabel().strip();
      }
      if (pathFact.outcome() != null && !pathFact.outcome().isBlank()) {
        outcome = pathFact.outcome().strip();
      }
    }
    if (japanese) {
      if (outcome.isBlank()) {
        return "分岐 `" + label + "` の結果を確認する。";
      }
      return "分岐 `" + label + "` の結果（" + outcome + "）を確認する。";
    }
    if (outcome.isBlank()) {
      return "Verify outcome for branch `" + label + "`.";
    }
    return "Verify branch `" + label + "` yields (" + outcome + ").";
  }

  private String extractTopLevelSectionBody(final String document, final int sectionNo) {
    if (document == null || document.isBlank() || sectionNo <= 0) {
      return "";
    }
    final int start = findSectionHeadingStart(document, sectionNo);
    if (start < 0) {
      return "";
    }
    final int from = document.indexOf('\n', start);
    if (from < 0) {
      return "";
    }
    int next = document.length();
    for (int candidate = sectionNo + 1; candidate <= 9; candidate++) {
      final int index = findSectionHeadingStart(document, candidate, from + 1);
      if (index >= 0 && index < next) {
        next = index;
      }
    }
    return document.substring(from + 1, next).strip();
  }

  private int findSectionHeadingStart(final String document, final int sectionNo) {
    return findSectionHeadingStart(document, sectionNo, 0);
  }

  private int findSectionHeadingStart(
      final String document, final int sectionNo, final int fromIndex) {
    // Regex matches "##" followed by optional space, then sectionNo, then either
    // dot or whitespace
    final java.util.regex.Pattern pattern =
        java.util.regex.Pattern.compile(
            "##\\s*" + sectionNo + "(?:\\.|\\s)", java.util.regex.Pattern.MULTILINE);
    final java.util.regex.Matcher matcher = pattern.matcher(document);
    if (matcher.find(fromIndex)) {
      return matcher.start();
    }
    return -1;
  }

  private boolean isConstructorMethod(final MethodInfo method, final String classSimpleName) {
    if (method == null || classSimpleName == null || classSimpleName.isBlank()) {
      return false;
    }
    final String methodName = method.getName() == null ? "" : method.getName().strip();
    if (classSimpleName.equals(methodName)) {
      return true;
    }
    final String signature = method.getSignature();
    if (signature == null || signature.isBlank()) {
      return false;
    }
    final String normalized = signature.strip().replace('$', '.').replace("`", "");
    final int paren = normalized.indexOf('(');
    if (paren <= 0) {
      return false;
    }
    String token = normalized.substring(0, paren).trim();
    final int space = token.lastIndexOf(' ');
    if (space >= 0 && space + 1 < token.length()) {
      token = token.substring(space + 1);
    }
    final int dot = token.lastIndexOf('.');
    if (dot >= 0 && dot + 1 < token.length()) {
      token = token.substring(dot + 1);
    }
    return classSimpleName.equals(token);
  }

  private String nullSafe(final String value) {
    return value != null ? value : msg(DOCUMENT_VALUE_NA);
  }

  private String formatExtendsInfo(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getExtendsTypes().isEmpty()) {
      return msg("document.value.none");
    }
    return PromptInputCanonicalizer.sortAndJoin(classInfo.getExtendsTypes(), ", ");
  }

  private String formatImplementsInfo(final ClassInfo classInfo) {
    if (classInfo == null || classInfo.getImplementsTypes().isEmpty()) {
      return msg("document.value.none");
    }
    return PromptInputCanonicalizer.sortAndJoin(classInfo.getImplementsTypes(), ", ");
  }

  private String promptTemplate() {
    return promptTemplateLoader.loadPromptTemplate();
  }

  private String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }

  private boolean isJapaneseLocale() {
    final Locale locale = MessageSource.getLocale();
    return locale == null || "ja".equalsIgnoreCase(locale.getLanguage());
  }

  private void recordGenerationDecision(
      final String classFqn, final GenerationMode mode, final List<String> reasons) {
    if (classFqn == null || classFqn.isBlank() || mode == null) {
      return;
    }
    generationDecisions.put(
        classFqn, new GenerationDecision(mode, reasons == null ? List.of() : List.copyOf(reasons)));
  }

  private void logGenerationModeSummary() {
    if (generationDecisions.isEmpty()) {
      return;
    }
    final EnumMap<GenerationMode, Integer> counts = new EnumMap<>(GenerationMode.class);
    for (final GenerationMode mode : GenerationMode.values()) {
      counts.put(mode, 0);
    }
    for (final GenerationDecision decision : generationDecisions.values()) {
      counts.computeIfPresent(decision.mode(), (k, v) -> v + 1);
    }
    Logger.info(
        String.format(
            Locale.ROOT,
            "LLM doc mode summary: direct=%d, retry=%d, fallback=%d, interface=%d",
            counts.getOrDefault(GenerationMode.DIRECT, 0),
            counts.getOrDefault(GenerationMode.RETRY, 0),
            counts.getOrDefault(GenerationMode.FALLBACK, 0),
            counts.getOrDefault(GenerationMode.INTERFACE, 0)));
    generationDecisions.entrySet().stream()
        .filter(entry -> entry.getValue().mode() == GenerationMode.FALLBACK)
        .forEach(
            entry ->
                Logger.info(
                    String.format(
                        Locale.ROOT,
                        "LLM doc fallback detail: class=%s, reasons=%s",
                        entry.getKey(),
                        String.join("; ", entry.getValue().reasons()))));
  }

  private enum GenerationMode {
    DIRECT,
    RETRY,
    FALLBACK,
    INTERFACE
  }

  private record MethodSubsections(
      String inputs,
      String preconditions,
      String postconditions,
      String normalFlow,
      String errorBoundaries,
      String dependencies,
      String testViewpoints) {

    private static MethodSubsections empty() {
      return new MethodSubsections("", "", "", "", "", "", "");
    }
  }

  private record PathViewpointFact(String pathLabel, String outcome) {}

  private record ValidationResult(boolean valid, List<String> reasons) {}

  private record GenerationDecision(GenerationMode mode, List<String> reasons) {}
}
