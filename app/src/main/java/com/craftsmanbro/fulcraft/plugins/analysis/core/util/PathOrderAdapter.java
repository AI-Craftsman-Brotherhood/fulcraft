package com.craftsmanbro.fulcraft.plugins.analysis.core.util;

import com.craftsmanbro.fulcraft.infrastructure.fs.impl.PathOrder;
import java.nio.file.Path;
import java.util.Comparator;

public final class PathOrderAdapter {

  public static final Comparator<Path> STABLE = PathOrder.STABLE;

  private PathOrderAdapter() {
    // Utility class
  }
}
