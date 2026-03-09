package com.craftsmanbro.fulcraft.infrastructure.coverage.contract;

/** Contract for loading line, branch, and method coverage percentages. */
public interface CoverageLoaderPort {

  /** Returns whether coverage data can currently be queried. */
  boolean isAvailable();

  /**
   * Returns line coverage percentage for the given fully qualified class name.
   *
   * @param classFqn fully qualified class name
   * @return line coverage percentage in the 0.0-100.0 range, or {@code -1} when unavailable
   */
  double getLineCoverage(String classFqn);

  /**
   * Returns branch coverage percentage for the given fully qualified class name.
   *
   * @param classFqn fully qualified class name
   * @return branch coverage percentage in the 0.0-100.0 range, or {@code -1} when unavailable
   */
  double getBranchCoverage(String classFqn);

  /**
   * Returns method coverage percentage for the given method signature in the target class.
   *
   * @param classFqn fully qualified class name
   * @param methodSignature method signature used by the coverage source
   * @return method coverage percentage in the 0.0-100.0 range, or {@code -1} when unavailable
   */
  double getMethodCoverage(String classFqn, String methodSignature);
}
