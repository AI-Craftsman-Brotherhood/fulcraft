package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.config.Config.GovernanceConfig.RedactionConfig;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.PromptSafety;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionContext;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectionResult;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DetectorChain;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.DictionaryDetector;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.Finding;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.MlNerDetector;
import com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety.redaction.detector.RegexDetector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies sensitive data redaction to prompts before sending to LLMs.
 *
 * <p>This service orchestrates a chain of sensitive data detectors (regex, dictionary, ML NER) and
 * applies masking or blocking based on configured thresholds.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Plugin-based detector chain (regex, dictionary, ML)
 *   <li>Configurable mask/block thresholds
 *   <li>Mode support: off, report (detect only), enforce (detect and act)
 *   <li>Backward compatible API
 * </ul>
 */
public final class PromptRedactionService {

  private static final Logger LOG = LoggerFactory.getLogger(PromptRedactionService.class);

  public static final String DEFAULT_MASK = "[REDACTED]";

  private final DetectorChain detectorChain;

  private final DetectionContext context;

  private final String mask;

  // Legacy mode: use old SensitiveDataRedactor
  private final SensitiveDataRedactor legacyRedactor;

  private final boolean useLegacyMode;

  /** Creates a service with default configuration (legacy mode for backward compatibility). */
  public PromptRedactionService() {
    this(new SensitiveDataRedactor());
  }

  /**
   * Creates a service with a legacy redactor (backward compatible).
   *
   * @param redactor Legacy sensitive data redactor
   */
  public PromptRedactionService(final SensitiveDataRedactor redactor) {
    this.legacyRedactor =
        Objects.requireNonNull(
            redactor,
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "redactor must not be null"));
    this.useLegacyMode = true;
    this.detectorChain = null;
    this.context = null;
    this.mask = DEFAULT_MASK;
  }

  /**
   * Creates a service with configuration.
   *
   * @param config Redaction configuration
   * @param projectRoot Project root for resolving dictionary paths
   */
  public PromptRedactionService(final RedactionConfig config, final Path projectRoot) {
    Objects.requireNonNull(
        config,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "config must not be null"));
    this.useLegacyMode = false;
    this.legacyRedactor = null;
    this.mask = DEFAULT_MASK;
    final Path resolvedRoot = resolveProjectRoot(projectRoot);
    // Build context from config
    this.context = buildContext(config, resolvedRoot);
    // Build detector chain from config
    this.detectorChain = buildDetectorChain(config, resolvedRoot);
    LOG.debug(
        "PromptRedactionService initialized with mode={}, detectors={}",
        config.getMode(),
        this.detectorChain.getDetectorNames());
  }

  /**
   * Creates a service from full Config.
   *
   * @param config Full application configuration
   * @param projectRoot Project root for resolving paths
   * @return Configured service
   */
  public static PromptRedactionService fromConfig(final Config config, final Path projectRoot) {
    if (config == null) {
      return new PromptRedactionService();
    }
    final RedactionConfig redactionConfig = config.getGovernance().getRedaction();
    if (redactionConfig.isOff()) {
      return new PromptRedactionService();
    }
    return new PromptRedactionService(redactionConfig, projectRoot);
  }

  private DetectionContext buildContext(final RedactionConfig config, final Path projectRoot) {
    final DetectionContext ctx = new DetectionContext();
    // Set mode
    final String mode = config.getMode();
    ctx.setMode(
        switch (mode) {
          case "off" -> DetectionContext.Mode.OFF;
          case "report" -> DetectionContext.Mode.REPORT;
          default -> DetectionContext.Mode.ENFORCE;
        });
    // Set thresholds
    ctx.setMaskThreshold(config.getMaskThreshold());
    ctx.setBlockThreshold(config.getBlockThreshold());
    // Set enabled detectors
    ctx.setEnabledDetectors(config.getDetectors());
    // Set dictionary paths
    if (config.getDenylistPath() != null && !config.getDenylistPath().isBlank()) {
      ctx.setDenylistPath(projectRoot.resolve(config.getDenylistPath()));
    }
    if (config.getAllowlistPath() != null && !config.getAllowlistPath().isBlank()) {
      final Path allowlistPath = projectRoot.resolve(config.getAllowlistPath());
      ctx.setAllowlistPath(allowlistPath);
      ctx.setAllowlistTerms(loadAllowlistTerms(allowlistPath));
    }
    // Set ML endpoint
    if (config.getMlEndpointUrl() != null && !config.getMlEndpointUrl().isBlank()) {
      ctx.setMlEndpointUrl(config.getMlEndpointUrl());
    }
    return ctx;
  }

  private DetectorChain buildDetectorChain(final RedactionConfig config, final Path projectRoot) {
    final DetectorChain.Builder builder = DetectorChain.builder();
    final List<String> detectors = config.getDetectors();
    for (final String detectorName : detectors) {
      switch (detectorName.toLowerCase(java.util.Locale.ROOT)) {
        case "regex" -> builder.add(new RegexDetector());
        case "dictionary" -> {
          final DictionaryDetector dictDetector = createDictionaryDetector(config, projectRoot);
          if (dictDetector != null) {
            builder.add(dictDetector);
          } else {
            LOG.warn(
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "infra.redaction.prompt.warn.dictionary_detector_missing_denylist"));
          }
        }
        case "ml" -> {
          final String mlUrl = config.getMlEndpointUrl();
          if (mlUrl != null && !mlUrl.isBlank()) {
            builder.add(new MlNerDetector(mlUrl));
          }
        }
        default ->
            LOG.warn(
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "infra.redaction.prompt.warn.unknown_detector", detectorName));
      }
    }
    return builder.build();
  }

  private DictionaryDetector createDictionaryDetector(
      final RedactionConfig config, final Path projectRoot) {
    final String denylistPathStr = config.getDenylistPath();
    if (denylistPathStr == null || denylistPathStr.isBlank()) {
      return null;
    }
    final Path denylistPath = projectRoot.resolve(denylistPathStr);
    Path allowlistPath = null;
    if (config.getAllowlistPath() != null && !config.getAllowlistPath().isBlank()) {
      allowlistPath = projectRoot.resolve(config.getAllowlistPath());
    }
    return DictionaryDetector.fromFiles(denylistPath, allowlistPath, false);
  }

  private Set<String> loadAllowlistTerms(final Path allowlistPath) {
    if (allowlistPath == null) {
      return Set.of();
    }
    if (!Files.exists(allowlistPath)) {
      LOG.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.prompt.warn.allowlist_missing", allowlistPath));
      return Set.of();
    }
    try {
      final List<String> lines = Files.readAllLines(allowlistPath, StandardCharsets.UTF_8);
      final Set<String> terms = new HashSet<>();
      for (final String line : lines) {
        final String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          terms.add(trimmed);
        }
      }
      return terms;
    } catch (Exception e) {
      LOG.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.prompt.warn.allowlist_load_failed", allowlistPath, e.getMessage()));
      return Set.of();
    }
  }

  private Path resolveProjectRoot(final Path projectRoot) {
    return projectRoot != null ? projectRoot : Path.of(".");
  }

  /**
   * Redacts sensitive data from a prompt.
   *
   * <p>This method maintains backward compatibility while supporting the new detector chain.
   *
   * @param prompt The prompt text to redact
   * @return Redaction result with redacted text and report
   * @throws RedactionException if blocking threshold is exceeded or an error occurs
   */
  public RedactionResult redactPrompt(final String prompt) {
    return redactPromptInternal(prompt, true, false);
  }

  public RedactionResult redactPromptWithoutContext(final String prompt) {
    return redactPromptInternal(prompt, false, false);
  }

  public RedactionResult redactPromptForStorage(final String prompt) {
    return redactPromptInternal(prompt, false, true);
  }

  private RedactionResult redactPromptInternal(
      final String prompt, final boolean trackContext, final boolean forceMask) {
    try {
      final String safePrompt = PromptSafety.addUntrustedPolicyIfNeeded(prompt);
      final RedactionResult result;
      if (useLegacyMode) {
        result = legacyRedactor.redact(safePrompt);
      } else {
        result = redactWithChain(safePrompt, forceMask);
      }
      if (trackContext) {
        RedactionContext.setPrompt(safePrompt);
        RedactionContext.setReport(result.report());
      }
      return result;
    } catch (RuntimeException e) {
      throw new RedactionException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Prompt redaction failed; aborting LLM request."),
          e);
    }
  }

  private RedactionResult redactWithChain(final String text, final boolean forceMask) {
    if (context.getMode() == DetectionContext.Mode.OFF) {
      return RedactionResult.unchanged(text);
    }
    // Run detection chain
    final DetectionResult detection = detectorChain.detect(text, context);
    if (!detection.hasFindings()) {
      return RedactionResult.unchanged(text);
    }
    final List<Finding> findings = detection.findings();
    final double maxConfidence = detection.maxConfidence();
    // Check for blocking condition
    if (context.shouldBlock(maxConfidence)) {
      final List<Finding> blockingFindings =
          findings.stream().filter(f -> f.confidence() >= context.getBlockThreshold()).toList();
      LOG.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.prompt.warn.blocked_findings",
              blockingFindings.size(),
              context.getBlockThreshold()));
      throw RedactionException.blocked(blockingFindings, context.getBlockThreshold());
    }
    // Report mode: return original text with findings info
    if (context.getMode() == DetectionContext.Mode.REPORT && !forceMask) {
      LOG.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.redaction.prompt.info.report_mode_detected", findings.size(), maxConfidence));
      final RedactionReport report = RedactionReport.fromFindings(findings);
      return new RedactionResult(text, report, findings, maxConfidence);
    }
    // Enforce mode (or forced masking): mask findings that exceed mask threshold
    final String maskedText = applyMasking(text, findings);
    final RedactionReport report = RedactionReport.fromFindings(findings);
    return new RedactionResult(maskedText, report, findings, maxConfidence);
  }

  private String applyMasking(final String text, final List<Finding> findings) {
    // Filter findings that should be masked
    final List<Finding> toMask =
        findings.stream()
            .filter(f -> f.confidence() >= context.getMaskThreshold())
            .sorted((a, b) -> Integer.compare(b.start(), a.start()))
            .toList();
    if (toMask.isEmpty()) {
      return text;
    }
    final StringBuilder sb = new StringBuilder(text);
    for (final Finding f : toMask) {
      sb.replace(f.start(), f.end(), mask);
    }
    LOG.debug(
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.redaction.prompt.debug.masked_findings", toMask.size()));
    return sb.toString();
  }

  /**
   * Returns whether this service uses the new detector chain mode.
   *
   * @return true if using detector chain, false if legacy mode
   */
  public boolean isDetectorChainMode() {
    return !useLegacyMode;
  }

  /**
   * Returns the list of enabled detector names.
   *
   * @return List of detector names, or legacy-regex if legacy mode
   */
  public List<String> getEnabledDetectors() {
    if (useLegacyMode || detectorChain == null) {
      return List.of("legacy-regex");
    }
    return detectorChain.getDetectorNames();
  }
}
