package com.craftsmanbro.fulcraft.plugins.analysis.interceptor;

import com.craftsmanbro.fulcraft.config.Config;
import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.kernel.pipeline.Hook;
import com.craftsmanbro.fulcraft.kernel.pipeline.PipelineNodeIds;
import com.craftsmanbro.fulcraft.kernel.pipeline.context.RunContext;
import com.craftsmanbro.fulcraft.kernel.pipeline.interceptor.PhaseInterceptor;
import com.craftsmanbro.fulcraft.logging.LoggerPort;
import com.craftsmanbro.fulcraft.logging.LoggerPortProvider;
import com.craftsmanbro.fulcraft.plugins.analysis.context.AnalysisResultContext;
import com.craftsmanbro.fulcraft.plugins.analysis.model.ClassInfo;
import com.craftsmanbro.fulcraft.plugins.analysis.model.MethodInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Filters out security-sensitive methods before the GENERATE phase.
 *
 * <p>This interceptor identifies methods that handle sensitive data (passwords, tokens, encryption)
 * and logs them for awareness. The selection stage should use this information to exclude these
 * methods from test generation.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Identify methods with security-related names or annotations
 *   <li>Log filtered methods for audit purposes
 *   <li>Add warnings to context about sensitive methods
 * </ul>
 */
public class SecurityFilterInterceptor implements PhaseInterceptor {

  private static final LoggerPort LOG =
      LoggerPortProvider.getLogger(SecurityFilterInterceptor.class);

  private static final String ID = "security-filter";

  public static final String METADATA_SENSITIVE_METHOD_IDS = "security.sensitive.method_ids";

  /** Method name patterns that indicate security-sensitive operations. */
  private static final Set<String> SENSITIVE_PATTERNS =
      Set.of(
          "password",
          "secret",
          "token",
          "credential",
          "encrypt",
          "decrypt",
          "authenticate",
          "authorize",
          "apikey",
          "privatekey",
          "signin",
          "signout",
          "login",
          "logout");

  /** Annotations that indicate security-sensitive methods. */
  private static final Set<String> SENSITIVE_ANNOTATIONS =
      Set.of(
          "PreAuthorize",
          "PostAuthorize",
          "Secured",
          "RolesAllowed",
          "PermitAll",
          "DenyAll",
          "Encrypt",
          "Decrypt");

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String phase() {
    return PipelineNodeIds.GENERATE;
  }

  @Override
  public Hook hook() {
    return Hook.PRE;
  }

  @Override
  public int order() {
    // Run after history loading but before main selection logic
    return 50;
  }

  @Override
  public boolean supports(final Config config) {
    // Always enabled for security
    return true;
  }

  @Override
  public void apply(final RunContext context) {
    LOG.debug(msg("analysis.security_filter.log.scan_start"));
    final var analysisResultOpt = AnalysisResultContext.get(context);
    if (analysisResultOpt.isEmpty()) {
      LOG.debug(msg("analysis.security_filter.log.no_analysis_result"));
      return;
    }
    final var analysisResult = analysisResultOpt.get();
    final Set<String> sensitiveMethodIds = new HashSet<>();
    final List<ClassInfo> classes = analysisResult.getClasses();
    for (final ClassInfo classInfo : classes) {
      if (classInfo == null) {
        continue;
      }
      scanClassForSensitiveMethods(classInfo, sensitiveMethodIds);
    }
    if (sensitiveMethodIds.isEmpty()) {
      return;
    }
    context.putMetadata(METADATA_SENSITIVE_METHOD_IDS, Set.copyOf(sensitiveMethodIds));
    LOG.info(
        msg("analysis.security_filter.log.found_sensitive_methods"),
        sensitiveMethodIds.size());
    context.addWarning(
        msg(
            "analysis.security_filter.warning.sensitive_methods_detected",
            sensitiveMethodIds.size()));
  }

  private void scanClassForSensitiveMethods(
      final ClassInfo classInfo, final Set<String> sensitiveMethodIds) {
    for (final MethodInfo method : classInfo.getMethods()) {
      if (method == null) {
        continue;
      }
      if (!isSensitiveMethod(method)) {
        continue;
      }
      addSensitiveMethodIds(classInfo, method, sensitiveMethodIds);
    }
  }

  /**
   * Determines if a method is security-sensitive based on its name and annotations.
   *
   * @param method the method to check
   * @return true if the method is considered sensitive
   */
  private boolean isSensitiveMethod(final MethodInfo method) {
    return hasSensitiveName(method) || hasSensitiveAnnotation(method);
  }

  private boolean hasSensitiveName(final MethodInfo method) {
    final String methodName = method.getName();
    if (methodName == null) {
      return false;
    }
    final String lowerMethodName = methodName.toLowerCase(Locale.ROOT);
    return SENSITIVE_PATTERNS.stream().anyMatch(lowerMethodName::contains);
  }

  private boolean hasSensitiveAnnotation(final MethodInfo method) {
    final List<String> annotations = method.getAnnotations();
    if (annotations == null || annotations.isEmpty()) {
      return false;
    }
    for (final String annotation : annotations) {
      if (annotation == null || annotation.isBlank()) {
        continue;
      }
      final String simpleName = extractSimpleAnnotationName(annotation);
      if (SENSITIVE_ANNOTATIONS.contains(simpleName)) {
        return true;
      }
    }
    return false;
  }

  private String extractSimpleAnnotationName(final String annotation) {
    if (annotation == null) {
      return "";
    }
    String name = annotation.startsWith("@") ? annotation.substring(1) : annotation;
    final int dotIndex = name.lastIndexOf('.');
    if (dotIndex >= 0) {
      name = name.substring(dotIndex + 1);
    }
    final int parenIndex = name.indexOf('(');
    if (parenIndex >= 0) {
      name = name.substring(0, parenIndex);
    }
    return name;
  }

  private void addSensitiveMethodIds(
      final ClassInfo classInfo, final MethodInfo method, final Set<String> sensitiveMethodIds) {
    final String methodId = method.getMethodId();
    if (methodId != null && !methodId.isBlank()) {
      sensitiveMethodIds.add(methodId);
      LOG.debug(
          msg("analysis.security_filter.log.found_sensitive_method"),
          methodId);
    }
    final String classFqn = classInfo.getFqn();
    final String methodName = method.getName();
    if (classFqn != null && !classFqn.isBlank() && methodName != null && !methodName.isBlank()) {
      final String simpleId = classFqn + "#" + methodName;
      if (sensitiveMethodIds.add(simpleId)) {
        LOG.debug(
            msg("analysis.security_filter.log.found_sensitive_method"),
            simpleId);
      }
    }
  }

  private static String msg(final String key, final Object... args) {
    return MessageSource.getMessage(key, args);
  }
}
