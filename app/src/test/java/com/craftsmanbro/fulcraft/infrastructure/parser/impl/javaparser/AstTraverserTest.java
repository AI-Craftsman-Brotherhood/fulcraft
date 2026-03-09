package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisContext;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.AnalysisResult;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.ClassInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.FieldInfo;
import com.craftsmanbro.fulcraft.infrastructure.parser.model.MethodInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AstTraverserTest {

  @Test
  void traverse_populatesClassInfoFieldsMethodsAndAnonymousClasses() {
    String source =
        """
        package com.example;

        import java.util.List;
        import com.example.other.External;

        public class Sample {
          private List<String> names;
          private External external;
          private Helper helper;

          public Sample(Dependency dep) {
            helper = new Helper();
          }

          void doWork() {
            helper();
            new Helper();
            Runnable r = new Runnable() {
              @Override
              public void run() {
                helper();
              }
            };
          }

          void helper() {}

          interface Repo {}
          abstract class AbstractThing {}
        }

        class Helper {}
        class Dependency {}
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    List<String> importStrings = toImportStrings(cu);
    RemovedApiDetector.RemovedApiImportInfo removedApiImports =
        RemovedApiDetector.fromImports(importStrings);

    AnalysisResult result = new AnalysisResult();
    AnalysisContext context = new AnalysisContext();

    DependencyGraphBuilder dependencyGraphBuilder = new DependencyGraphBuilder();
    AstTraverser traverser = new AstTraverser(dependencyGraphBuilder);

    Path srcRoot = Path.of("/project/src/main/java");
    Path path = srcRoot.resolve("com/example/Sample.java");

    traverser.traverse(cu, result, srcRoot, path, importStrings, removedApiImports, context);

    ClassInfo sample =
        result.getClasses().stream()
            .filter(c -> "com.example.Sample".equals(c.getFqn()))
            .findFirst()
            .orElseThrow();

    assertTrue(sample.hasNestedClasses());
    assertEquals("com/example/Sample.java", sample.getFilePath());

    assertTrue(hasFieldType(sample, "java.util.List<String>"));
    assertTrue(hasFieldType(sample, "com.example.other.External"));
    assertTrue(hasFieldType(sample, "com.example.Helper"));

    MethodInfo doWork =
        sample.getMethods().stream()
            .filter(m -> "doWork".equals(m.getName()))
            .findFirst()
            .orElseThrow();

    String doWorkKey =
        DependencyGraphBuilder.methodKey("com.example.Sample", doWork.getSignature());
    assertTrue(context.getMethodInfos().containsKey(doWorkKey));
    assertTrue(Boolean.TRUE.equals(context.getMethodHasBody().get(doWorkKey)));
    assertNotNull(context.getMethodCodeHash().get(doWorkKey));

    String helperCallKey = DependencyGraphBuilder.methodKey("com.example.Sample", "helper(0)");
    assertTrue(context.getCallGraph().get(doWorkKey).contains(helperCallKey));

    Optional<ClassInfo> anonymousClass =
        result.getClasses().stream().filter(ClassInfo::isAnonymous).findFirst();
    assertTrue(anonymousClass.isPresent());
    assertTrue(anonymousClass.get().getFqn().contains("$anonymous@"));
    assertTrue(anonymousClass.get().getExtendsTypes().contains("Runnable"));
    assertFalse(anonymousClass.get().getMethods().isEmpty());
  }

  private static boolean hasFieldType(ClassInfo info, String type) {
    return info.getFields().stream().map(FieldInfo::getType).anyMatch(type::equals);
  }

  private static List<String> toImportStrings(CompilationUnit cu) {
    List<String> imports = new ArrayList<>();
    cu.getImports()
        .forEach(
            i -> {
              String name = i.getNameAsString();
              if (i.isAsterisk()) {
                name = name + ".*";
              }
              if (i.isStatic()) {
                name = "static " + name;
              }
              imports.add(name);
            });
    return imports;
  }
}
