package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.common.CodeHashing;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.Test;

class CodeHasherTest {

  @Test
  void computeCodeHash_matchesNormalizedBodyAndHandlesMissingBodies() {
    String source =
        """
        package com.example;

        abstract class Sample {
          Sample() {}

          void foo() {
            int value = 1;
            if (value > 0) {
              value++;
            }
          }

          abstract void noBody();
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(source);
    ClassOrInterfaceDeclaration sample = cu.getClassByName("Sample").orElseThrow();

    MethodDeclaration foo = sample.getMethodsByName("foo").get(0);
    MethodDeclaration noBody = sample.getMethodsByName("noBody").get(0);
    ConstructorDeclaration ctor = sample.getConstructors().get(0);

    assertTrue(CodeHasher.hasBody(foo));
    assertFalse(CodeHasher.hasBody(noBody));
    assertTrue(CodeHasher.hasBody(ctor));

    String expected = CodeHashing.hashNormalized(foo.getBody().orElseThrow().toString());
    assertEquals(expected, CodeHasher.computeCodeHash(foo).orElseThrow());
    assertTrue(CodeHasher.computeCodeHash(noBody).isEmpty());
  }
}
