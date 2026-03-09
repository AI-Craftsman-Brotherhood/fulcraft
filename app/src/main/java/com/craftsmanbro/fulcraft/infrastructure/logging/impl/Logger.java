package com.craftsmanbro.fulcraft.infrastructure.logging.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.contract.OperationalLoggingPort;
import com.craftsmanbro.fulcraft.infrastructure.logging.model.LogContext;
import com.craftsmanbro.fulcraft.infrastructure.security.impl.SecretMasker;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * L: Logging helper backed by SLF4J/Logback. Provides consistent formatting and log trimming for
 * large outputs.
 *
 * <p>This class is the central logging facility for FUL, providing:
 *
 * <ul>
 *   <li>Unified log format with configurable output (human/JSON)
 *   <li>MDC (Mapped Diagnostic Context) for trace IDs and subsystem context
 *   <li>Color output with TTY detection and configuration
 *   <li>Secret masking for sensitive information
 *   <li>Large content trimming to prevent log bloat
 *   <li>Progress bar and i18n support
 * </ul>
 */
public final class Logger implements OperationalLoggingPort {

  private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("utgenerator");

  // 10KB default
  private static final int DEFAULT_MAX_LOG_LENGTH = 10_000;

  private static final String TRIM_MARKER =
      "\n... [TRIMMED: showing first and last portions] ...\n";

  // Appender name constants
  private static final String APPENDER_STDOUT = "STDOUT";

  private static final String APPENDER_STDERR = "STDERR";

  private static final String APPENDER_APP_FILE = "APP_FILE";

  private static final String APPENDER_APP_TEXT_FILE = "APP_TEXT_FILE";

  private static final String APPENDER_LLM_FILE = "LLM_FILE";

  // MDC Keys
  public static final String MDC_RUN_ID = "runId";

  public static final String MDC_TRACE_ID = "traceId";

  public static final String MDC_SUBSYSTEM = "subsystem";

  public static final String MDC_STAGE = "stage";

  public static final String MDC_TARGET_CLASS = "targetClass";

  public static final String MDC_TASK_ID = "taskId";

  // Track emitted warnings to prevent log spam (thread-safe)
  private static final Set<String> EMITTED_WARNINGS = ConcurrentHashMap.newKeySet();

  // Track emitted info messages to prevent log spam (thread-safe)
  private static final Set<String> EMITTED_INFOS = ConcurrentHashMap.newKeySet();

  // Configuration state
  private static final AtomicReference<Config.LogConfig> LOG_CONFIG =
      new AtomicReference<>(new Config.LogConfig());

  private static final AtomicBoolean COLOR_ENABLED = new AtomicBoolean(true);

  private static final AtomicBoolean JSON_MODE = new AtomicBoolean(false);

  private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

  // ANSI Color Constants
  public static final String ANSI_RESET = "\u001B[0m";

  public static final String ANSI_BLACK = "\u001B[30m";

  public static final String ANSI_RED = "\u001B[31m";

  public static final String ANSI_GREEN = "\u001B[32m";

  public static final String ANSI_YELLOW = "\u001B[33m";

  public static final String ANSI_BLUE = "\u001B[34m";

  public static final String ANSI_PURPLE = "\u001B[35m";

  public static final String ANSI_CYAN = "\u001B[36m";

  public static final String ANSI_WHITE = "\u001B[37m";

  public static final String ANSI_CLEAR_LINE = "\u001B[2K";

  /** Wraps text in green ANSI color codes if color is enabled. */
  public static String green(final String text) {
    if (!COLOR_ENABLED.get()) {
      return text;
    }
    return ANSI_GREEN + text + ANSI_RESET;
  }

  /** Wraps text in red ANSI color codes if color is enabled. */
  public static String red(final String text) {
    if (!COLOR_ENABLED.get()) {
      return text;
    }
    return ANSI_RED + text + ANSI_RESET;
  }

  /** Wraps text in yellow ANSI color codes if color is enabled. */
  public static String yellow(final String text) {
    if (!COLOR_ENABLED.get()) {
      return text;
    }
    return ANSI_YELLOW + text + ANSI_RESET;
  }

  /** Wraps text in cyan ANSI color codes if color is enabled. */
  public static String cyan(final String text) {
    if (!COLOR_ENABLED.get()) {
      return text;
    }
    return ANSI_CYAN + text + ANSI_RESET;
  }

  /** Wraps text in bold ANSI codes if color is enabled. */
  public static String bold(final String text) {
    if (!COLOR_ENABLED.get()) {
      return text;
    }
    return "\u001B[1m" + text + ANSI_RESET;
  }

  // Progress bar configuration
  private static final int DEFAULT_BAR_WIDTH = 30;

  private static final int FILENAME_TRUNCATE_LIMIT = 40;

  private static final int FILENAME_DISPLAY_SUFFIX_LENGTH = 37;

  private static final ThreadLocal<Boolean> PROGRESS_SUPPRESSED =
      ThreadLocal.withInitial(() -> Boolean.FALSE);

  private static final PrintStream DEFAULT_PROGRESS_OUT = System.out;

  private static final PrintStream DEFAULT_PROGRESS_ERR = System.err;

  private static final AtomicReference<PrintStream> PROGRESS_OUT =
      new AtomicReference<>(DEFAULT_PROGRESS_OUT);

  private static final AtomicReference<PrintStream> PROGRESS_ERR =
      new AtomicReference<>(DEFAULT_PROGRESS_ERR);

  // ETR Calculation state
  private static final long ETR_MIN_ELAPSED_MS = 2000;

  private Logger() {}

  public static OperationalLoggingPort port() {
    return INSTANCE;
  }

  // ======================= Configuration =======================
  /**
   * Initializes the Logger with configuration settings. Should be called once at application
   * startup after loading config.
   *
   * @param config the application configuration
   */
  public static void initialize(final Config config) {
    if (config == null) {
      return;
    }
    // Apply environment variable overrides
    applyEnvironmentOverrides(config);
    final Config.LogConfig cfg = config.getLog();
    LOG_CONFIG.set(cfg);
    // Apply color setting
    if (config.getCli() != null && config.getCli().getColor() != null) {
      final boolean cliColor;
      final String color = config.getCli().getColor();
      if ("on".equalsIgnoreCase(color)) {
        cliColor = true;
      } else if ("off".equalsIgnoreCase(color)) {
        cliColor = false;
      } else {
        cliColor = System.console() != null;
      }
      COLOR_ENABLED.set(cliColor);
    } else {
      COLOR_ENABLED.set(cfg.shouldEnableColor());
    }
    // Apply structured mode (JSON/YAML)
    JSON_MODE.set(cfg.isJsonFormat() || cfg.isYamlFormat());
    // Apply log level
    setLogLevel(cfg.toLogbackLevel());
    // Configure Logback layout (JSON/YAML)
    configureLogback(cfg);
    // Generate initial trace ID if MDC is enabled
    if (cfg.isEnableMdc()) {
      initializeTraceId();
    }
    INITIALIZED.set(true);
    debug(
        "Logger INITIALIZED with format="
            + cfg.getFormat()
            + ", level="
            + cfg.getLevel()
            + ", color="
            + COLOR_ENABLED.get());
  }

  private static void configureLogback(final Config.LogConfig cfg) {
    try {
      final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
      if (!(factory instanceof LoggerContext context)) {
        return;
      }
      final ch.qos.logback.classic.Logger root =
          context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      configureConsoleAppenders(cfg, context, root);
      configureFileAppenders(cfg, context, root);
    } catch (Exception e) {
      LOG.error(
          MessageSource.getMessage("infra.logging.logger.error.configure_logback_failed"),
          e.getMessage());
    }
  }

  private static void configureConsoleAppenders(
      final Config.LogConfig cfg,
      final LoggerContext context,
      final ch.qos.logback.classic.Logger root) {
    final boolean consoleEnabled = isConsoleOutput(cfg.getOutput());
    final Appender<ILoggingEvent> stdoutAppender = root.getAppender(APPENDER_STDOUT);
    final Appender<ILoggingEvent> stderrAppender = root.getAppender(APPENDER_STDERR);
    if (!consoleEnabled) {
      root.detachAppender(APPENDER_STDOUT);
      root.detachAppender(APPENDER_STDERR);
      return;
    }
    attachAppender(root, stdoutAppender);
    attachAppender(root, stderrAppender);
    if (stdoutAppender instanceof OutputStreamAppender<ILoggingEvent> stdout) {
      configureConsoleLayout(cfg, context, stdout, false);
    }
    if (stderrAppender instanceof OutputStreamAppender<ILoggingEvent> stderr) {
      configureConsoleLayout(cfg, context, stderr, true);
    }
  }

  private static void configureConsoleLayout(
      final Config.LogConfig cfg,
      final LoggerContext context,
      final OutputStreamAppender<ILoggingEvent> appender,
      final boolean includeLevel) {
    final var encoder = appender.getEncoder();
    if (!(encoder instanceof LayoutWrappingEncoder<ILoggingEvent> layoutEncoder)) {
      return;
    }
    final String format = cfg.getFormat();
    if ("yaml".equalsIgnoreCase(format)) {
      final var layout = new YamlLayout();
      layout.setContext(context);
      layout.start();
      layoutEncoder.setLayout(layout);
    } else if ("json".equalsIgnoreCase(format)) {
      final var layout = new JsonLayout();
      layout.setContext(context);
      layout.start();
      layoutEncoder.setLayout(layout);
    } else {
      final var layout = new MaskedPatternLayout();
      layout.setContext(context);
      layout.setPattern(buildConsolePattern(cfg, includeLevel));
      layout.start();
      layoutEncoder.setLayout(layout);
    }
  }

  private static void configureFileAppenders(
      final Config.LogConfig cfg,
      final LoggerContext context,
      final ch.qos.logback.classic.Logger root) {
    final boolean fileEnabled = isFileOutput(cfg.getOutput());
    final Appender<ILoggingEvent> appFile = root.getAppender(APPENDER_APP_FILE);
    final Appender<ILoggingEvent> appTextFile = root.getAppender(APPENDER_APP_TEXT_FILE);
    if (!fileEnabled) {
      root.detachAppender(APPENDER_APP_FILE);
      root.detachAppender(APPENDER_APP_TEXT_FILE);
      return;
    }
    final boolean structured =
        "json".equalsIgnoreCase(cfg.getFormat()) || "yaml".equalsIgnoreCase(cfg.getFormat());
    if (structured) {
      attachAppender(root, appFile);
      root.detachAppender(APPENDER_APP_TEXT_FILE);
      if (appFile instanceof OutputStreamAppender<ILoggingEvent> osAppender) {
        configureFileLayout(cfg, context, osAppender);
      }
      if (appFile instanceof RollingFileAppender<ILoggingEvent> rollingAppender) {
        updateRollingFileAppender(
            rollingAppender, cfg.getFilePath(), buildTimeBasedPattern(cfg.getFilePath()), context);
      }
    } else {
      attachAppender(root, appTextFile);
      root.detachAppender(APPENDER_APP_FILE);
      if (appTextFile instanceof OutputStreamAppender<ILoggingEvent> osAppender) {
        configureFileLayout(cfg, context, osAppender);
      }
      if (appTextFile instanceof RollingFileAppender<ILoggingEvent> rollingAppender) {
        updateRollingFileAppender(
            rollingAppender,
            cfg.getFilePath(),
            buildSizeAndTimePattern(cfg.getFilePath()),
            context);
      }
    }
  }

  private static void configureFileLayout(
      final Config.LogConfig cfg,
      final LoggerContext context,
      final OutputStreamAppender<ILoggingEvent> appender) {
    final var encoder = appender.getEncoder();
    if (!(encoder instanceof LayoutWrappingEncoder<ILoggingEvent> layoutEncoder)) {
      return;
    }
    final String format = cfg.getFormat();
    if ("yaml".equalsIgnoreCase(format)) {
      final var layout = new YamlLayout();
      layout.setContext(context);
      layout.start();
      layoutEncoder.setLayout(layout);
    } else if ("json".equalsIgnoreCase(format)) {
      final var layout = new JsonLayout();
      layout.setContext(context);
      layout.start();
      layoutEncoder.setLayout(layout);
    } else {
      final var layout = new MaskedPatternLayout();
      layout.setContext(context);
      layout.setPattern(buildFilePattern(cfg));
      layout.start();
      layoutEncoder.setLayout(layout);
    }
  }

  private static void updateRollingFileAppender(
      final RollingFileAppender<ILoggingEvent> appender,
      final String filePath,
      final String fileNamePattern,
      final LoggerContext context) {
    if (filePath == null || filePath.isBlank()) {
      return;
    }
    appender.stop();
    appender.setFile(filePath);
    final var policy = appender.getRollingPolicy();
    if (policy instanceof TimeBasedRollingPolicy<?> timePolicy) {
      timePolicy.stop();
      timePolicy.setContext(context);
      timePolicy.setParent(appender);
      timePolicy.setFileNamePattern(fileNamePattern);
      timePolicy.start();
    } else if (policy instanceof SizeAndTimeBasedRollingPolicy<?> sizePolicy) {
      sizePolicy.stop();
      sizePolicy.setContext(context);
      sizePolicy.setParent(appender);
      sizePolicy.setFileNamePattern(fileNamePattern);
      sizePolicy.start();
    }
    appender.start();
  }

  private static void attachAppender(
      final ch.qos.logback.classic.Logger root, final Appender<ILoggingEvent> appender) {
    if (appender != null && !root.isAttached(appender)) {
      root.addAppender(appender);
    }
  }

  private static boolean isConsoleOutput(final String output) {
    return output == null || "console".equalsIgnoreCase(output) || "both".equalsIgnoreCase(output);
  }

  private static boolean isFileOutput(final String output) {
    return "file".equalsIgnoreCase(output) || "both".equalsIgnoreCase(output);
  }

  private static String buildConsolePattern(
      final Config.LogConfig cfg, final boolean includeLevel) {
    final var pattern = new StringBuilder();
    if (cfg.isIncludeTimestamp()) {
      pattern.append("%d{HH:mm:ss} ");
    }
    if (cfg.isIncludeThread()) {
      pattern.append("[%thread] ");
    }
    if (cfg.isIncludeLogger()) {
      pattern.append("%logger{36} - ");
    }
    if (includeLevel) {
      pattern.append("[%level] ");
    }
    pattern.append("%msg%n");
    return pattern.toString();
  }

  private static String buildFilePattern(final Config.LogConfig cfg) {
    final var pattern = new StringBuilder();
    pattern.append("%d{yyyy-MM-dd HH:mm:ss.SSS} ");
    if (cfg.isIncludeThread()) {
      pattern.append("[%thread] ");
    }
    if (cfg.isEnableMdc()) {
      pattern.append("[%X{runId:-}] [%X{traceId:-}] [%X{subsystem:-}] [%X{stage:-}] ");
    }
    pattern.append("%-5level ");
    if (cfg.isIncludeLogger()) {
      pattern.append("%logger{36} - ");
    }
    pattern.append("%msg%n");
    return pattern.toString();
  }

  private static String buildTimeBasedPattern(final String filePath) {
    String base = filePath;
    String extension = "";
    final int separatorIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    final int dotIndex = filePath.lastIndexOf('.');
    if (dotIndex > separatorIndex) {
      base = filePath.substring(0, dotIndex);
      extension = filePath.substring(dotIndex);
    }
    return base + ".%d{yyyy-MM-dd}" + extension;
  }

  private static String buildSizeAndTimePattern(final String filePath) {
    String base = filePath;
    String extension = "";
    final int separatorIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    final int dotIndex = filePath.lastIndexOf('.');
    if (dotIndex > separatorIndex) {
      base = filePath.substring(0, dotIndex);
      extension = filePath.substring(dotIndex);
    }
    return base + ".%d{yyyy-MM-dd}.%i" + extension;
  }

  /**
   * Applies environment variable overrides to the configuration.
   *
   * <p>Supported environment variables:
   *
   * <ul>
   *   <li>FUL_COLOR - Color mode: "on", "off", or "auto"
   *   <li>FUL_LOG_FORMAT - Log format: "human", "json", or "yaml"
   *   <li>NO_COLOR - When set (any value), disables color output (per https://no-color.org/)
   * </ul>
   */
  private static void applyEnvironmentOverrides(final Config config) {
    // Check NO_COLOR first (standard env var)
    final String noColor = System.getenv("NO_COLOR");
    if (noColor != null && !noColor.isEmpty()) {
      if (config.getCli() == null) {
        config.setCli(new Config.CliConfig());
      }
      config.getCli().setColor("off");
    }
    // FUL_COLOR overrides NO_COLOR if set
    final String colorEnv = System.getenv("FUL_COLOR");
    if (colorEnv != null && !colorEnv.isEmpty()) {
      if (config.getCli() == null) {
        config.setCli(new Config.CliConfig());
      }
      config.getCli().setColor(colorEnv);
    }
    // FUL_LOG_FORMAT overrides config log format
    final String logFormatEnv = System.getenv("FUL_LOG_FORMAT");
    if (logFormatEnv != null && !logFormatEnv.isEmpty()) {
      final Config.LogConfig logConfig = config.getLog();
      config.setLog(logConfig);
      logConfig.setFormat(logFormatEnv);
    }
  }

  /** Returns true if the Logger has been INITIALIZED with configuration. */
  public static boolean isInitialized() {
    return INITIALIZED.get();
  }

  /** Returns the current log configuration. */
  public static Config.LogConfig getConfig() {
    return LOG_CONFIG.get();
  }

  /** Sets the log level programmatically. */
  public static void setLogLevel(final Level level) {
    final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
    if (factory instanceof LoggerContext context) {
      final var rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      rootLogger.setLevel(level);
      final var appLogger = context.getLogger("com.craftsmanbro.fulcraft");
      appLogger.setLevel(level);
      final var utgLogger = context.getLogger("utgenerator");
      utgLogger.setLevel(level);
    }
  }

  /** Enables or disables color output. */
  public static void setColorEnabled(final boolean enabled) {
    COLOR_ENABLED.set(enabled);
  }

  /** Returns true if color output is enabled. */
  public static boolean isColorEnabled() {
    return COLOR_ENABLED.get();
  }

  /** Enables or disables structured output mode (JSON/YAML). */
  public static void setJsonMode(final boolean enabled) {
    JSON_MODE.set(enabled);
  }

  /**
   * Configures run-scoped logging to write files under the given run directory.
   *
   * @param config the application configuration
   * @param runRoot the run output root directory
   * @param runId the run identifier for MDC
   */
  public static void configureRunLogging(
      final Config config, final Path runRoot, final String runId) {
    if (config == null || runRoot == null) {
      return;
    }
    Config.LogConfig cfg = config.getLog();
    if (cfg == null) {
      cfg = new Config.LogConfig();
      config.setLog(cfg);
    }
    LOG_CONFIG.set(cfg);
    final Path logDir = runRoot.resolve("logs");
    try {
      Files.createDirectories(logDir);
    } catch (Exception e) {
      LOG.warn(
          MessageSource.getMessage("infra.logging.logger.warn.create_run_log_dir_failed"),
          e.getMessage());
    }
    final String configuredPath = cfg.getFilePath();
    String fileName = null;
    if (configuredPath != null && !configuredPath.isBlank()) {
      final Path configured = Path.of(configuredPath);
      final Path configuredFileName = configured.getFileName();
      fileName = configuredFileName != null ? configuredFileName.toString() : null;
    }
    if (fileName == null || fileName.isBlank()) {
      fileName = "ful.log";
    }
    cfg.setFilePath(logDir.resolve(fileName).toString());
    refreshFileAppenders(cfg);
    refreshLlmAppender(logDir);
    if (cfg.isEnableMdc() && runId != null && !runId.isBlank()) {
      setRunId(runId);
    }
  }

  /** Returns true if structured output mode (JSON/YAML) is enabled. */
  public static boolean isJsonMode() {
    return JSON_MODE.get();
  }

  // ======================= MDC Context Management =======================
  /**
   * Initializes a new trace ID for the current thread. Call this at the start of a new
   * request/operation.
   */
  public static void initializeTraceId() {
    MDC.put(MDC_TRACE_ID, UUID.randomUUID().toString().substring(0, 8));
  }

  /**
   * Sets the trace ID for the current thread.
   *
   * @param traceId the trace ID to set
   */
  public static void setTraceId(final String traceId) {
    if (traceId != null) {
      MDC.put(MDC_TRACE_ID, traceId);
    }
  }

  /** Gets the current trace ID, or null if not set. */
  public static String getTraceId() {
    return MDC.get(MDC_TRACE_ID);
  }

  /** Sets the run ID for the current thread's MDC context. */
  public static void setRunId(final String runId) {
    if (runId != null && !runId.isBlank()) {
      MDC.put(MDC_RUN_ID, runId);
    }
  }

  /** Returns the current run ID from MDC. */
  public static String getRunId() {
    return MDC.get(MDC_RUN_ID);
  }

  /**
   * Sets the current subsystem name for logging context.
   *
   * @param subsystem the subsystem name (e.g., "analysis", "generation", "llm")
   */
  public static void setSubsystem(final String subsystem) {
    if (subsystem != null) {
      MDC.put(MDC_SUBSYSTEM, subsystem);
    }
  }

  /**
   * Sets the current stage name for logging context.
   *
   * @param stage the stage name (e.g., "AnalyzeStage", "GenerateStage")
   */
  public static void setStage(final String stage) {
    if (stage != null) {
      MDC.put(MDC_STAGE, stage);
    }
  }

  /**
   * Sets the target class being processed for logging context.
   *
   * @param className the fully qualified class name
   */
  public static void setTargetClass(final String className) {
    if (className != null) {
      MDC.put(MDC_TARGET_CLASS, className);
    }
  }

  /**
   * Sets the current task ID for logging context.
   *
   * @param taskId the task identifier
   */
  public static void setTaskId(final String taskId) {
    if (taskId != null) {
      MDC.put(MDC_TASK_ID, taskId);
    }
  }

  /**
   * Clears all MDC context for the current thread. Call this when the current request/operation is
   * complete.
   */
  public static void clearContext() {
    MDC.clear();
  }

  /** Clears task-specific MDC context while preserving trace ID and subsystem. */
  public static void clearTaskContext() {
    MDC.remove(MDC_TARGET_CLASS);
    MDC.remove(MDC_TASK_ID);
    MDC.remove(MDC_STAGE);
  }

  @Override
  public void emitInfo(final String message) {
    info(message);
  }

  @Override
  public void emitDebug(final String message) {
    debug(message);
  }

  @Override
  public void emitWarn(final String message) {
    warn(message);
  }

  @Override
  public void emitWarn(final String message, final Throwable throwable) {
    warn(message, throwable);
  }

  @Override
  public void emitError(final String message) {
    error(message);
  }

  @Override
  public void emitError(final String message, final Throwable throwable) {
    error(message, throwable);
  }

  @Override
  public void emitStdout(final String message) {
    stdout(message);
  }

  @Override
  public void emitStderr(final String message) {
    stderr(message);
  }

  @Override
  public void applyContext(final LogContext context) {
    if (context == null || !context.hasAny()) {
      return;
    }
    setRunId(context.runId());
    setTraceId(context.traceId());
    setSubsystem(context.subsystem());
    setStage(context.stage());
    setTargetClass(context.targetClass());
    setTaskId(context.taskId());
  }

  @Override
  public LogContext currentContext() {
    return new LogContext(
        getRunId(),
        getTraceId(),
        MDC.get(MDC_SUBSYSTEM),
        MDC.get(MDC_STAGE),
        MDC.get(MDC_TARGET_CLASS),
        MDC.get(MDC_TASK_ID));
  }

  private static void refreshFileAppenders(final Config.LogConfig cfg) {
    try {
      final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
      if (!(factory instanceof LoggerContext context)) {
        return;
      }
      final ch.qos.logback.classic.Logger root =
          context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      configureFileAppenders(cfg, context, root);
    } catch (Exception e) {
      LOG.warn(
          MessageSource.getMessage("infra.logging.logger.warn.refresh_file_appenders_failed"),
          e.getMessage());
    }
  }

  private static void refreshLlmAppender(final Path logDir) {
    try {
      final ILoggerFactory factory = LoggerFactory.getILoggerFactory();
      if (!(factory instanceof LoggerContext context)) {
        return;
      }
      final ch.qos.logback.classic.Logger llmLogger = context.getLogger("llm");
      final Appender<ILoggingEvent> appender = llmLogger.getAppender(APPENDER_LLM_FILE);
      if (appender instanceof RollingFileAppender<ILoggingEvent> rollingAppender) {
        final String filePath = logDir.resolve("llm.log").toString();
        updateRollingFileAppender(
            rollingAppender, filePath, buildSizeAndTimePattern(filePath), context);
      }
    } catch (Exception e) {
      LOG.warn(
          MessageSource.getMessage("infra.logging.logger.warn.refresh_llm_appender_failed"),
          e.getMessage());
    }
  }

  // ======================= Legacy Compatibility =======================
  /** Adjust the root log level (INFO or DEBUG). */
  public static void setDebugEnabled(final boolean enabled) {
    setLogLevel(enabled ? Level.DEBUG : Level.INFO);
  }

  /** Override the stream used for progress output (info/warn/error go through SLF4J). */
  public static void setOutput(final PrintStream stdout, final PrintStream stderr) {
    PROGRESS_OUT.set(stdout != null ? stdout : DEFAULT_PROGRESS_OUT);
    PROGRESS_ERR.set(stderr != null ? stderr : DEFAULT_PROGRESS_ERR);
  }

  /** Suppresses progress output while the returned scope is open. */
  public static ProgressOutputSilencer suppressProgressOutput() {
    return new ProgressOutputSilencer(PROGRESS_SUPPRESSED.get());
  }

  public static final class ProgressOutputSilencer implements AutoCloseable {

    private final boolean previousSuppressed;

    private ProgressOutputSilencer(final boolean previousSuppressed) {
      this.previousSuppressed = previousSuppressed;
      PROGRESS_SUPPRESSED.set(Boolean.TRUE);
    }

    @Override
    public void close() {
      if (previousSuppressed) {
        PROGRESS_SUPPRESSED.set(Boolean.TRUE);
      } else {
        PROGRESS_SUPPRESSED.remove();
      }
    }
  }

  // ======================= Standard Logging Methods =======================
  // Standard info logging
  public static void info(final String message) {
    LOG.info(message);
  }

  // Debug logging (only when enabled)
  public static void debug(final String message) {
    LOG.debug(message);
  }

  // Warning logging
  public static void warn(final String message) {
    LOG.warn(message);
  }

  public static void warn(final String message, final Throwable t) {
    LOG.warn(message, t);
  }

  /**
   * Logs an info message only once per unique key during the application lifetime. Subsequent calls
   * with the same key are silently ignored.
   *
   * @param key unique identifier for this message (e.g., "analysis:source-dir")
   * @param message the info message to log
   */
  public static void infoOnce(final String key, final String message) {
    if (key != null && EMITTED_INFOS.add(key)) {
      LOG.info(message);
    }
  }

  /**
   * Logs a warning only once per unique key during the application lifetime. Subsequent calls with
   * the same key are silently ignored.
   *
   * @param key unique identifier for this warning (e.g., "providerName:SEED")
   * @param message the warning message to log
   */
  public static void warnOnce(final String key, final String message) {
    if (key != null && EMITTED_WARNINGS.add(key)) {
      LOG.warn(message);
    }
  }

  /** Reset emitted warnings. Intended for testing purposes only. */
  public static void resetWarnOnceKeys() {
    EMITTED_WARNINGS.clear();
  }

  /** Reset emitted info messages. Intended for testing purposes only. */
  public static void resetInfoOnceKeys() {
    EMITTED_INFOS.clear();
  }

  // Error logging
  public static void error(final String message) {
    LOG.error(message);
  }

  public static void error(final String message, final Throwable t) {
    LOG.error(message, t);
  }

  // Progress logging (no timestamp, inline style)
  public static void progress(final String message) {
    if (isJsonMode()) {
      LOG.info(MessageSource.getMessage("infra.logging.logger.info.progress_message"), message);
    } else {
      PROGRESS_OUT.get().println(maskForOutput("  → " + message));
    }
  }

  /** Plain stdout line for CLI-like reports. */
  public static void stdout(final String message) {
    if (isJsonMode()) {
      LOG.info(message);
    } else {
      PROGRESS_OUT.get().println(maskForOutput(message));
    }
  }

  /** Plain stdout output without newline for prompts. */
  public static void stdoutInline(final String message) {
    if (isJsonMode()) {
      LOG.info(message);
    } else {
      PROGRESS_OUT.get().print(maskForOutput(message));
      PROGRESS_OUT.get().flush();
    }
  }

  /** Plain stderr line for CLI-like reports. */
  public static void stderr(final String message) {
    if (isJsonMode()) {
      LOG.error(message);
    } else {
      PROGRESS_ERR.get().println(maskForOutput(message));
    }
  }

  /** Prints a success message to stdout (GREEN). */
  public static void stdoutSuccess(final String message) {
    stdout(green("✔ " + message));
  }

  /** Prints a warning message to stdout (YELLOW). */
  public static void stdoutWarn(final String message) {
    stdout(yellow("⚠ " + message));
  }

  /** Prints an error message to stderr (RED). */
  public static void stdoutError(final String message) {
    stderr(red("✘ " + message));
  }

  /** Prints an info message to stdout (BLUE/BOLD). */
  public static void stdoutInfo(final String message) {
    stdout(bold(cyan("ℹ " + message)));
  }

  /**
   * A1: Real-time progress bar with ETR.
   *
   * @param current current number of items processed
   * @param total total number of items
   * @param currentFile name of the current file being processed
   * @param startTimeMs system time in ms when the task started
   */
  public static void progressBar(
      final int current, final int total, final String currentFile, final long startTimeMs) {
    if (Boolean.TRUE.equals(PROGRESS_SUPPRESSED.get())) {
      return;
    }
    if (isJsonMode()) {
      // Fallback to basic JSON logging
      LOG.info(
          MessageSource.getMessage("infra.logging.logger.info.progress_ratio"),
          current,
          total,
          currentFile);
      return;
    }
    double ratio = total > 0 ? (double) current / total : 0.0;
    ratio = Math.clamp(ratio, 0.0, 1.0);
    final int filled = (int) (ratio * DEFAULT_BAR_WIDTH);
    final var bar = new StringBuilder("[");
    bar.append("█".repeat(filled));
    bar.append("░".repeat(DEFAULT_BAR_WIDTH - filled));
    bar.append("]");
    // Calculate ETR
    String etrStr = "";
    if (current > 0 && total > 0 && startTimeMs > 0) {
      final long elapsed = System.currentTimeMillis() - startTimeMs;
      if (elapsed > ETR_MIN_ELAPSED_MS) {
        final double msPerItem = (double) elapsed / current;
        final long remainingItems = (long) total - current;
        final long remainingMs = (long) (msPerItem * remainingItems);
        etrStr = " (ETR: " + formatDuration(remainingMs) + ")";
      }
    }
    // Truncate filename if too long
    var fileName = maskForOutput(currentFile);
    if (fileName.length() > FILENAME_TRUNCATE_LIMIT) {
      fileName = "..." + fileName.substring(fileName.length() - FILENAME_DISPLAY_SUFFIX_LENGTH);
    }
    // Use clear line if color enabled, otherwise fallback to padding
    if (COLOR_ENABLED.get()) {
      // Clear line and return to start
      PROGRESS_OUT.get().print(ANSI_CLEAR_LINE + "\r");
      // Print status
      final String status =
          String.format("  %s %3d%% %s %s", bar, (int) (ratio * 100), fileName, etrStr);
      PROGRESS_OUT.get().print(status);
    } else {
      // Fallback: use CR and padding (safer width 80)
      String status =
          String.format("\r  %s %3d%% %s %s", bar, (int) (ratio * 100), fileName, etrStr);
      final int padding = Math.max(0, 80 - status.length());
      status += " ".repeat(padding);
      PROGRESS_OUT.get().print(status);
    }
    PROGRESS_OUT.get().flush();
    // Print newline when complete
    if ((total > 0 && current >= total) || (total <= 0 && current == total)) {
      PROGRESS_OUT.get().println();
    }
  }

  public static void progressBar(final int current, final int total, final String currentFile) {
    progressBar(current, total, currentFile, 0);
  }

  private static String formatDuration(final long ms) {
    long seconds = ms / 1000;
    if (seconds < 60) {
      return seconds + "s";
    }
    long minutes = seconds / 60;
    seconds = seconds % 60;
    if (minutes < 60) {
      return minutes + "m " + seconds + "s";
    }
    final long hours = minutes / 60;
    minutes = minutes % 60;
    return hours + "h " + minutes + "m";
  }

  // A1: Progress completion message
  public static void progressComplete(final int total) {
    if (Boolean.TRUE.equals(PROGRESS_SUPPRESSED.get())) {
      return;
    }
    final String msg = "  → " + MessageSource.getMessage("analysis.complete", total);
    if (isJsonMode()) {
      LOG.info(msg);
    } else {
      PROGRESS_OUT.get().println(maskForOutput(msg));
    }
  }

  // A1: Progress completion message (logged once per total)
  public static void progressCompleteOnce(final int total) {
    if (Boolean.TRUE.equals(PROGRESS_SUPPRESSED.get())) {
      return;
    }
    final String msg = "  → " + MessageSource.getMessage("analysis.complete", total);
    final String key = "progress.complete:" + total;
    if (!EMITTED_INFOS.add(key)) {
      return;
    }
    if (isJsonMode()) {
      LOG.info(msg);
      return;
    }
    PROGRESS_OUT.get().println(maskForOutput(msg));
  }

  // === i18n-aware logging methods ===
  /**
   * Logs an info message using a message key from the resource bundle.
   *
   * @param key the message key
   * @param args optional formatting arguments
   */
  public static void infoI18n(final String key, final Object... args) {
    if (LOG.isInfoEnabled()) {
      LOG.info(MessageSource.getMessage(key, args));
    }
  }

  /**
   * Logs a warning message using a message key from the resource bundle.
   *
   * @param key the message key
   * @param args optional formatting arguments
   */
  public static void warnI18n(final String key, final Object... args) {
    if (LOG.isWarnEnabled()) {
      LOG.warn(MessageSource.getMessage(key, args));
    }
  }

  /**
   * Logs an error message using a message key from the resource bundle.
   *
   * @param key the message key
   * @param args optional formatting arguments
   */
  public static void errorI18n(final String key, final Object... args) {
    if (LOG.isErrorEnabled()) {
      LOG.error(MessageSource.getMessage(key, args));
    }
  }

  /**
   * Prints a localized message to stdout.
   *
   * @param key the message key
   * @param args optional formatting arguments
   */
  public static void stdoutI18n(final String key, final Object... args) {
    final String msg = MessageSource.getMessage(key, args);
    if (isJsonMode()) {
      LOG.info(msg);
    } else {
      PROGRESS_OUT.get().println(maskForOutput(msg));
    }
  }

  /**
   * Prints a localized progress message.
   *
   * @param key the message key
   * @param args optional formatting arguments
   */
  public static void progressI18n(final String key, final Object... args) {
    final String msg = "  → " + MessageSource.getMessage(key, args);
    if (isJsonMode()) {
      LOG.info(msg);
    } else {
      PROGRESS_OUT.get().println(maskForOutput(msg));
    }
  }

  // Section header
  public static void section(final String title) {
    if (isJsonMode()) {
      LOG.info(MessageSource.getMessage("infra.logging.logger.info.section_title"), title);
    } else {
      PROGRESS_OUT.get().println(maskForOutput("\n=== " + title + " ==="));
    }
  }

  /**
   * L: Trim large strings to prevent log bloat. Shows first and last portions with a trim marker in
   * between.
   */
  public static String trimLargeContent(final String content, final int maxLength) {
    if (content == null) {
      return null;
    }
    int effectiveMaxLength = maxLength;
    if (effectiveMaxLength <= 0) {
      final int configuredMax = LOG_CONFIG.get().getMaxMessageLength();
      if (configuredMax > 0) {
        effectiveMaxLength = configuredMax;
      } else if (isInitialized()) {
        return content;
      } else {
        effectiveMaxLength = DEFAULT_MAX_LOG_LENGTH;
      }
    }
    return StringUtils.abbreviateMiddle(content, TRIM_MARKER, effectiveMaxLength);
  }

  /** L: Trim with default max length */
  public static String trimLargeContent(final String content) {
    return trimLargeContent(content, DEFAULT_MAX_LOG_LENGTH);
  }

  /** L: Log large content with auto-trimming */
  public static void infoLarge(final String label, final String content) {
    info(label + ":\n" + trimLargeContent(content));
  }

  private static final Logger INSTANCE = new Logger();

  public static void logLlm(final String type, final String content) {
    final org.slf4j.Logger llmLogger = LlmLoggerHolder.LOGGER;
    if (llmLogger.isInfoEnabled()) {
      llmLogger.info(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "[{}] {}"),
          type,
          content);
    }
  }

  public static void debugLarge(final String label, final String content) {
    if (LOG.isDebugEnabled()) {
      debug(label + ":\n" + trimLargeContent(content));
    }
  }

  // === E1: Exception summarization helpers ===
  /**
   * Logs a warning with summarized exception info. Uses default LOG mode options.
   *
   * @param t the exception to summarize
   */
  public static void warnException(final Throwable t) {
    if (LOG.isWarnEnabled()) {
      LOG.warn(
          ExceptionSummarizer.summarizeAsString(t, ExceptionSummarizer.SummaryOptions.defaults()));
    }
  }

  /**
   * Logs a warning with a custom message and summarized exception info.
   *
   * @param message the custom message prefix
   * @param t the exception to summarize
   */
  public static void warnException(final String message, final Throwable t) {
    if (LOG.isWarnEnabled()) {
      LOG.warn(
          "{}\n{}",
          message,
          ExceptionSummarizer.summarizeAsString(t, ExceptionSummarizer.SummaryOptions.defaults()));
    }
  }

  /**
   * Logs an error with summarized exception info. Uses default LOG mode options.
   *
   * @param t the exception to summarize
   */
  public static void errorException(final Throwable t) {
    if (LOG.isErrorEnabled()) {
      LOG.error(
          ExceptionSummarizer.summarizeAsString(t, ExceptionSummarizer.SummaryOptions.defaults()));
    }
  }

  /**
   * Logs an error with a custom message and summarized exception info.
   *
   * @param message the custom message prefix
   * @param t the exception to summarize
   */
  public static void errorException(final String message, final Throwable t) {
    if (LOG.isErrorEnabled()) {
      LOG.error(
          "{}\n{}",
          message,
          ExceptionSummarizer.summarizeAsString(t, ExceptionSummarizer.SummaryOptions.defaults()));
    }
  }

  private static String maskForOutput(final String message) {
    final String masked = SecretMasker.mask(message);
    return masked == null ? "" : masked;
  }

  private static final class LlmLoggerHolder {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger("llm");
  }
}
