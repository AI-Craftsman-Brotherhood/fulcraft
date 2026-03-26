package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade for project symbol indexing within the analysis package.
 *
 * <p>This class provides a stable API for building and querying symbol indices, abstracting the
 * underlying implementation details.
 */
public final class SymbolIndex {

  private final ProjectSymbolIndex delegate;

  private SymbolIndex(final ProjectSymbolIndex delegate) {
    this.delegate = Objects.requireNonNull(delegate);
  }

  /**
   * Builds a symbol index from the given source roots.
   *
   * @param sourceRoots the source directories to index
   * @return the symbol index
   */
  public static SymbolIndex build(final List<Path> sourceRoots) {
    Objects.requireNonNull(
        sourceRoots,
        MessageSource.getMessage(
            "analysis.common.error.argument_null", "sourceRoots must not be null"));
    final ProjectSymbolIndex index = new ProjectSymbolIndexBuilder().build(sourceRoots);
    return new SymbolIndex(index);
  }

  /**
   * Wraps an existing project symbol index without rebuilding it.
   *
   * @param index the existing symbol index
   * @return the symbol index facade
   */
  public static SymbolIndex wrap(final ProjectSymbolIndex index) {
    return new SymbolIndex(
        Objects.requireNonNull(
            index,
            MessageSource.getMessage(
                "analysis.common.error.argument_null", "index must not be null")));
  }

  /**
   * Checks if a class exists in the index.
   *
   * @param fqn the fully qualified name or simple name
   * @return true if the class exists
   */
  public boolean hasClass(final String fqn) {
    return delegate.hasClass(fqn);
  }

  /**
   * Checks if a method exists in the index.
   *
   * @param className the class name
   * @param methodName the method name
   * @return true if the method exists
   */
  public boolean hasMethod(final String className, final String methodName) {
    return delegate.hasMethod(className, methodName);
  }

  /**
   * Checks if a method exists in the index with the given arity.
   *
   * @param className the class name
   * @param methodName the method name
   * @param paramCount the parameter count
   * @return true if the method exists with the given arity
   */
  public boolean hasMethodArity(
      final String className, final String methodName, final int paramCount) {
    return delegate.hasMethodArity(className, methodName, paramCount);
  }

  /**
   * Counts the number of overloads found in the index for a method name.
   *
   * @param className the class name
   * @param methodName the method name
   * @return number of overloads
   */
  public int getMethodOverloadCount(final String className, final String methodName) {
    return delegate.getMethodOverloadCount(className, methodName);
  }

  /**
   * Checks if a method signature exists in the index.
   *
   * @param className the class name
   * @param signature the method signature
   * @return true if the signature exists
   */
  public boolean hasMethodSignature(final String className, final String signature) {
    return delegate.hasMethodSignature(className, signature);
  }

  /**
   * Checks if a field exists in the index.
   *
   * @param className the class name
   * @param fieldName the field name
   * @return true if the field exists
   */
  public boolean hasField(final String className, final String fieldName) {
    return delegate.hasField(className, fieldName);
  }

  /**
   * Gets fields for a class (name -> type).
   *
   * @param classFqn the class fully qualified name
   * @return fields mapped by name
   */
  public Map<String, String> getFields(final String classFqn) {
    return delegate.getFields(classFqn);
  }

  /**
   * Finds class candidates by simple name.
   *
   * @param simpleName the simple class name
   * @param limit maximum number of candidates to return
   * @return list of matching FQNs
   */
  public List<String> findClassCandidates(final String simpleName, final int limit) {
    return delegate.findClassCandidates(simpleName, limit);
  }

  /**
   * Gets the underlying ProjectSymbolIndex for compatibility.
   *
   * @return the underlying index
   */
  public ProjectSymbolIndex unwrap() {
    return delegate;
  }
}
