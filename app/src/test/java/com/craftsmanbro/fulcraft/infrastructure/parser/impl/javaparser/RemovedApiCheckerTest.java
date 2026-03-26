package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.RemovedApiDetector;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RemovedApiCheckerTest {

  @Test
  void detectsRemovedApiUsageViaImportsAndMethodCallScope() {
    String source =
        """
        package com.example;

        import javax.xml.bind.JAXBContext;

        class Sample {
          void use() throws Exception {
            JAXBContext.newInstance("x");
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    List<String> importStrings = toImportStrings(cu);
    RemovedApiDetector.RemovedApiImportInfo importInfo =
        RemovedApiDetector.fromImports(importStrings);

    MethodCallExpr call = cu.findFirst(MethodCallExpr.class).orElseThrow();

    assertTrue(RemovedApiChecker.isRemovedApiUsage(call, importInfo));
    assertTrue(RemovedApiChecker.usesRemovedApis(cu, importInfo));
  }

  @Test
  void ignoresNonRemovedApiTypes() {
    String source =
        """
        package com.example;

        import java.util.List;

        class Sample {
          void use() {
            List.of("a", "b");
          }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);

    assertFalse(RemovedApiChecker.usesRemovedApis(cu, null));
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
