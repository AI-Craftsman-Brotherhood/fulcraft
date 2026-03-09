package com.craftsmanbro.fulcraft.infrastructure.parser.javaparser.brittle.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.AbstractJavaParserBrittleRule;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractJavaParserBrittleRuleTest {

  @Test
  void check_shouldThrowUnsupportedOperationException() {
    AbstractJavaParserBrittleRule rule = new TestRule();

    assertThrows(
        UnsupportedOperationException.class,
        () -> rule.check("Test.java", "class Test {}", List.of()));
  }

  @Test
  void createFinding_usesLineNumberAndEvidenceFromNode() {
    String content =
        """
        class Test {
            void test() {
                doThing();
            }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(content);
    AbstractJavaParserBrittleRule rule = new TestRule();
    List<BrittleFinding> findings = rule.checkAst(cu, "Test.java");

    assertEquals(1, findings.size());
    BrittleFinding finding = findings.get(0);
    assertEquals(RuleId.TIME, finding.ruleId());
    assertEquals(Severity.WARNING, finding.severity());
    assertEquals("Test.java", finding.filePath());
    assertEquals(3, finding.lineNumber());
    assertEquals("doThing()", finding.evidence());
  }

  @Test
  void createFindingForObjectCreation_usesLineNumberAndEvidenceFromNode() {
    String content =
        """
        class Test {
            void test() {
                new Helper();
            }
        }
        """;

    CompilationUnit cu = StaticJavaParser.parse(content);
    AbstractJavaParserBrittleRule rule = new ObjectCreationTestRule();
    List<BrittleFinding> findings = rule.checkAst(cu, "Test.java");

    assertEquals(1, findings.size());
    BrittleFinding finding = findings.get(0);
    assertEquals(RuleId.RANDOM, finding.ruleId());
    assertEquals(Severity.ERROR, finding.severity());
    assertEquals("Test.java", finding.filePath());
    assertEquals(3, finding.lineNumber());
    assertEquals("new Helper()", finding.evidence());
  }

  private static final class TestRule extends AbstractJavaParserBrittleRule {
    private TestRule() {
      super(Severity.WARNING);
    }

    @Override
    public RuleId getRuleId() {
      return RuleId.TIME;
    }

    @Override
    public List<BrittleFinding> checkAst(CompilationUnit cu, String filePath) {
      MethodCallExpr call = cu.findFirst(MethodCallExpr.class).orElseThrow();
      return List.of(createFinding(call, filePath, "message"));
    }
  }

  private static final class ObjectCreationTestRule extends AbstractJavaParserBrittleRule {
    private ObjectCreationTestRule() {
      super(Severity.ERROR);
    }

    @Override
    public RuleId getRuleId() {
      return RuleId.RANDOM;
    }

    @Override
    public List<BrittleFinding> checkAst(CompilationUnit cu, String filePath) {
      ObjectCreationExpr objectCreation = cu.findFirst(ObjectCreationExpr.class).orElseThrow();
      return List.of(createFinding(objectCreation, filePath, "message"));
    }
  }
}
