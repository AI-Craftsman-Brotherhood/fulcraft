package com.craftsmanbro.fulcraft.plugins.analysis.core.service.index;

import com.craftsmanbro.fulcraft.i18n.MessageSource;
import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.AstUtils;
import com.craftsmanbro.fulcraft.plugins.analysis.core.util.PathOrderAdapter;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Builds and caches a lightweight project-wide symbol index. */
public class ProjectSymbolIndexBuilder {

  private static final Map<String, ProjectSymbolIndex> CACHE = new ConcurrentHashMap<>();

  private final JavaParser parser = new JavaParser();

  public ProjectSymbolIndex build(final List<Path> sourceRoots) {
    final String cacheKey = buildCacheKey(sourceRoots);
    return CACHE.computeIfAbsent(cacheKey, key -> buildIndex(sourceRoots));
  }

  public void update(final ProjectSymbolIndex index, final Path sourceFile) {
    if (index == null || sourceFile == null || !Files.isRegularFile(sourceFile)) {
      return;
    }
    parseFileIntoIndex(index, sourceFile);
  }

  private ProjectSymbolIndex buildIndex(final List<Path> sourceRoots) {
    final ProjectSymbolIndex index = new ProjectSymbolIndex();
    if (sourceRoots == null || sourceRoots.isEmpty()) {
      return index;
    }
    for (final Path root : sourceRoots) {
      if (root == null || !Files.isDirectory(root)) {
        continue;
      }
      scanRoot(index, root);
    }
    return index;
  }

  private void scanRoot(final ProjectSymbolIndex index, final Path root) {
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .filter(
              path -> {
                final Path fileName = path.getFileName();
                return fileName != null && fileName.toString().endsWith(".java");
              })
          .sorted(PathOrderAdapter.STABLE)
          .forEach(path -> parseFileIntoIndex(index, path));
    } catch (IOException e) {
      Logger.warn(
          MessageSource.getMessage("analysis.symbol_index.scan_failed", root, e.getMessage()));
    }
  }

  private void parseFileIntoIndex(final ProjectSymbolIndex index, final Path path) {
    try {
      final ParseResult<CompilationUnit> result = parser.parse(path);
      if (!result.isSuccessful()) {
        return;
      }
      result
          .getResult()
          .ifPresent(
              cu -> {
                final String packageName =
                    cu.getPackageDeclaration()
                        .map(com.github.javaparser.ast.nodeTypes.NodeWithName::getNameAsString)
                        .orElse("");
                for (final TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                  if (isLocalType(type)) {
                    continue;
                  }
                  final String fqn = buildTypeFqn(type, packageName);
                  index.addClass(fqn);
                  collectMembers(index, type, fqn);
                }
              });
    } catch (Exception e) {
      Logger.debug(
          MessageSource.getMessage("analysis.symbol_index.parse_failed", path, e.getMessage()));
    }
  }

  private boolean isLocalType(final TypeDeclaration<?> type) {
    return AstUtils.findAncestor(type, MethodDeclaration.class).isPresent()
        || AstUtils.findAncestor(type, ConstructorDeclaration.class).isPresent()
        || AstUtils.findAncestor(type, InitializerDeclaration.class).isPresent();
  }

  private void collectMembers(
      final ProjectSymbolIndex index, final TypeDeclaration<?> type, final String fqn) {
    if (type instanceof ClassOrInterfaceDeclaration classOrInterfaceDecl) {
      addMethods(index, fqn, classOrInterfaceDecl.getMethods());
      addConstructors(index, fqn, classOrInterfaceDecl.getConstructors());
      addFields(index, fqn, classOrInterfaceDecl.getFields());
      return;
    }
    if (type instanceof EnumDeclaration enumDecl) {
      addMethods(index, fqn, enumDecl.getMethods());
      addFields(index, fqn, enumDecl.getFields());
      return;
    }
    if (type instanceof RecordDeclaration recordDecl) {
      addMethods(index, fqn, recordDecl.getMethods());
      addRecordComponents(index, fqn, recordDecl);
    }
  }

  private void addMethods(
      final ProjectSymbolIndex index, final String fqn, final List<MethodDeclaration> methods) {
    for (final MethodDeclaration method : methods) {
      final String name = method.getNameAsString();
      final String signature = method.getSignature().asString();
      final int paramCount = method.getParameters().size();
      index.addMethod(fqn, name, signature, paramCount);
    }
  }

  private void addConstructors(
      final ProjectSymbolIndex index,
      final String fqn,
      final List<ConstructorDeclaration> constructors) {
    for (final ConstructorDeclaration constructor : constructors) {
      final String name = constructor.getNameAsString();
      final String signature = constructor.getSignature().asString();
      final int paramCount = constructor.getParameters().size();
      index.addMethod(fqn, name, signature, paramCount);
    }
  }

  private void addFields(
      final ProjectSymbolIndex index, final String fqn, final List<FieldDeclaration> fields) {
    for (final FieldDeclaration field : fields) {
      final String typeName = field.getCommonType().asString();
      field
          .getVariables()
          .forEach(variable -> index.addField(fqn, variable.getNameAsString(), typeName));
    }
  }

  private void addRecordComponents(
      final ProjectSymbolIndex index, final String fqn, final RecordDeclaration recordDecl) {
    recordDecl
        .getParameters()
        .forEach(param -> index.addField(fqn, param.getNameAsString(), param.getType().asString()));
  }

  private String buildTypeFqn(final TypeDeclaration<?> type, final String packageName) {
    final List<String> names = new ArrayList<>();
    TypeDeclaration<?> current = type;
    while (current != null) {
      names.add(0, current.getNameAsString());
      current =
          current
              .getParentNode()
              .filter(TypeDeclaration.class::isInstance)
              .map(TypeDeclaration.class::cast)
              .orElse(null);
    }
    final String joined = String.join(".", names);
    if (packageName == null || packageName.isBlank()) {
      return joined;
    }
    return packageName + "." + joined;
  }

  private String buildCacheKey(final List<Path> sourceRoots) {
    if (sourceRoots == null || sourceRoots.isEmpty()) {
      return "empty";
    }
    final List<String> parts = new ArrayList<>();
    for (final Path root : sourceRoots) {
      if (root != null) {
        parts.add(root.toAbsolutePath().normalize().toString());
      }
    }
    parts.sort(String::compareTo);
    return String.join("|", parts);
  }
}
