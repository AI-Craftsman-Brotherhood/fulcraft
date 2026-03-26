package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import org.apache.commons.lang3.StringUtils;

/**
 * Utility for building method keys used in analysis graphs.
 *
 * <p>Note: This is a copy of {@code feature.analysis.core.util.MethodKeyUtil} moved to the
 * infrastructure layer to eliminate the reverse dependency on feature internals.
 */
public final class MethodKeyUtil {

  public static final String SEPARATOR = "#";

  private MethodKeyUtil() {}

  public static String methodKey(
      final String classFqn, final String signature, final boolean treatBlankAsUnknown) {
    final String normalizedClassFqn = StringUtils.strip(classFqn);
    final String cls;
    if (treatBlankAsUnknown) {
      cls = StringUtils.isBlank(normalizedClassFqn) ? MethodInfo.UNKNOWN : normalizedClassFqn;
    } else {
      cls = normalizedClassFqn == null ? MethodInfo.UNKNOWN : normalizedClassFqn;
    }
    final String sig = StringUtils.stripToEmpty(signature);
    return cls + SEPARATOR + sig;
  }
}
