package com.craftsmanbro.fulcraft.infrastructure.parser.impl.spoon;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.MethodKeyUtil;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ResolutionStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/** Builds the dependency graph (call graph) for Spoon analysis. Extracted from SpoonAnalyzer. */
public class DependencyGraphBuilder {

  /**
   * Generates a unique key for a method based on its class and signature.
   *
   * @param classFqn The fully qualified class name.
   * @param signature The method signature.
   * @return The unique key.
   */
  public static String methodKey(final String classFqn, final String signature) {
    return MethodKeyUtil.methodKey(classFqn, signature, true);
  }

  /**
   * Collects method calls from a given executable and updates the analysis context.
   *
   * @param executable The Spoon executable (method/constructor) to traverse.
   * @param currentMethodKey The key of the method currently being analyzed.
   * @param context The analysis context to update.
   */
  public void collectCalledMethods(
      final CtExecutable<?> executable,
      final String currentMethodKey,
      final AnalysisContext context) {
    Objects.requireNonNull(executable);
    Objects.requireNonNull(context);
    if (StringUtils.isBlank(currentMethodKey)) {
      return;
    }
    final var calls = context.getOrCreateCallGraphEntry(currentMethodKey);
    for (final var inv : executable.getElements(new TypeFilter<>(CtInvocation.class))) {
      addEdge(resolveMethodCall(inv), currentMethodKey, context, calls);
    }
    for (final var cc : executable.getElements(new TypeFilter<>(CtConstructorCall.class))) {
      addEdge(resolveConstructorCall(cc), currentMethodKey, context, calls);
    }
    for (final var ref :
        executable.getElements(new TypeFilter<>(CtExecutableReferenceExpression.class))) {
      addEdge(resolveMethodReference(ref), currentMethodKey, context, calls);
    }
  }

  private ResolvedCall resolveMethodCall(final CtInvocation<?> invocation) {
    final var executableReference = invocation.getExecutable();
    final String declaringType =
        resolveDeclaringType(
            executableReference != null ? executableReference.getDeclaringType() : null,
            invocation.getTarget() != null ? invocation.getTarget().getType() : null);
    final String signature =
        executableReference != null && StringUtils.isNotBlank(executableReference.getSignature())
            ? executableReference.getSignature()
            : SafeSpoonPrinter.safeToString(invocation);
    return new ResolvedCall(
        methodKey(declaringType, signature),
        resolutionStatus(declaringType),
        extractArgumentLiterals(invocation.getArguments()));
  }

  private ResolvedCall resolveConstructorCall(final CtConstructorCall<?> constructorCall) {
    final String declaringType = resolveDeclaringType(constructorCall.getType(), null);
    final String signature;
    if (constructorCall.getExecutable() != null
        && StringUtils.isNotBlank(constructorCall.getExecutable().getSignature())) {
      signature = constructorCall.getExecutable().getSignature();
    } else if (constructorCall.getType() != null) {
      signature = constructorCall.getType().getSimpleName() + "()";
    } else {
      signature = "ctor()";
    }
    return new ResolvedCall(
        methodKey(declaringType, signature),
        resolutionStatus(declaringType),
        extractArgumentLiterals(constructorCall.getArguments()));
  }

  private ResolvedCall resolveMethodReference(
      final CtExecutableReferenceExpression<?, ?> executableReferenceExpression) {
    final var executableReference = executableReferenceExpression.getExecutable();
    final String declaringType =
        resolveDeclaringType(
            executableReference != null ? executableReference.getDeclaringType() : null,
            executableReferenceExpression.getTarget() != null
                ? executableReferenceExpression.getTarget().getType()
                : null);
    final String signature =
        executableReference != null && StringUtils.isNotBlank(executableReference.getSignature())
            ? executableReference.getSignature()
            : SafeSpoonPrinter.safeToString(executableReferenceExpression);
    return new ResolvedCall(
        methodKey(declaringType, signature), resolutionStatus(declaringType), List.of());
  }

  private String resolveDeclaringType(
      final CtTypeReference<?> primaryType, final CtTypeReference<?> fallbackType) {
    final String primary = resolveTypeName(primaryType);
    return StringUtils.isNotBlank(primary) ? primary : resolveTypeName(fallbackType);
  }

  private String resolveTypeName(final CtTypeReference<?> typeReference) {
    if (typeReference == null) {
      return null;
    }
    final String qualifiedName = StringUtils.stripToNull(typeReference.getQualifiedName());
    if (qualifiedName != null) {
      return qualifiedName;
    }
    return StringUtils.stripToNull(typeReference.getSimpleName());
  }

  private ResolutionStatus resolutionStatus(final String declaringType) {
    return StringUtils.isNotBlank(declaringType)
        ? ResolutionStatus.RESOLVED
        : ResolutionStatus.UNRESOLVED;
  }

  private List<String> extractArgumentLiterals(final List<? extends CtExpression<?>> arguments) {
    if (arguments == null || arguments.isEmpty()) {
      return List.of();
    }
    final LinkedHashSet<String> literals = new LinkedHashSet<>();
    for (final CtExpression<?> argument : arguments) {
      final String literal = extractLiteralArgument(argument);
      if (StringUtils.isBlank(literal)) {
        continue;
      }
      literals.add(literal);
    }
    return literals.isEmpty() ? List.of() : new ArrayList<>(literals);
  }

  private String extractLiteralArgument(final CtExpression<?> argument) {
    if (argument == null) {
      return null;
    }
    if (argument instanceof CtLiteral<?>) {
      return normalizeLiteralSnippet(SafeSpoonPrinter.safeToString(argument));
    }
    if (argument instanceof CtUnaryOperator<?> unaryOperator && isSignedLiteral(unaryOperator)) {
      return normalizeLiteralSnippet(SafeSpoonPrinter.safeToString(unaryOperator));
    }
    return null;
  }

  private boolean isSignedLiteral(final CtUnaryOperator<?> unaryOperator) {
    final UnaryOperatorKind kind = unaryOperator.getKind();
    return (kind == UnaryOperatorKind.NEG || kind == UnaryOperatorKind.POS)
        && unaryOperator.getOperand() instanceof CtLiteral<?>;
  }

  private String normalizeLiteralSnippet(final String literal) {
    if (StringUtils.isBlank(literal)) {
      return "";
    }
    final String normalized = literal.replaceAll("\\s+", " ").strip();
    if (normalized.length() <= 120) {
      return normalized;
    }
    return normalized.substring(0, 117) + "...";
  }

  private void addEdge(
      final ResolvedCall target,
      final String callerKey,
      final AnalysisContext context,
      final Set<String> calls) {
    if (target == null || StringUtils.isBlank(target.signature())) {
      return;
    }
    calls.add(target.signature());
    context.getIncomingCounts().merge(target.signature(), 1, (a, b) -> a + b);
    context.recordCallStatus(callerKey, target.signature(), target.status());
    context.recordCallArgumentLiterals(callerKey, target.signature(), target.argumentLiterals());
  }

  private record ResolvedCall(
      String signature, ResolutionStatus status, List<String> argumentLiterals) {}
}
