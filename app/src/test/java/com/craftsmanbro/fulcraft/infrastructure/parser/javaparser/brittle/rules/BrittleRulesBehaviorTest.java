package com.craftsmanbro.fulcraft.infrastructure.parser.javaparser.brittle.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.AbstractJavaParserBrittleRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.BrittleRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.OverMockRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.RandomRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.ReflectionRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.SleepRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.TimeRule;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrittleRulesBehaviorTest {

  private static List<BrittleFinding> checkWithAst(
      AbstractJavaParserBrittleRule rule, String content) {
    CompilationUnit cu = StaticJavaParser.parse(content);
    return rule.checkAst(cu, "SampleTest.java");
  }

  @Test
  void brittleRule_isEnabledReturnsTrueByDefault() {
    BrittleRule rule =
        new BrittleRule() {
          @Override
          public RuleId getRuleId() {
            return RuleId.TIME;
          }

          @Override
          public Severity getDefaultSeverity() {
            return Severity.WARNING;
          }

          @Override
          public List<BrittleFinding> check(String filePath, String content, List<String> lines) {
            return List.of();
          }
        };

    assertTrue(rule.isEnabled());
  }

  @Nested
  class TimeRuleTests {

    @Test
    void exposesRuleIdAndDefaultSeverity() {
      TimeRule rule = new TimeRule();

      assertEquals(RuleId.TIME, rule.getRuleId());
      assertEquals(Severity.WARNING, rule.getDefaultSeverity());
    }

    @Test
    void ignoresNowCall_whenClockIsInjectedAsArgument() {
      String content =
          """
          class Test {
              void test(java.time.Clock injectedClock) {
                  java.time.Instant.now(injectedClock);
              }
          }
          """;

      TimeRule rule = new TimeRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsStaticClockFactoryCall_whenImportedStatically() {
      String content =
          """
          import static java.time.Clock.systemUTC;
          class Test {
              void test() {
                  systemUTC();
              }
          }
          """;

      TimeRule rule = new TimeRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.TIME, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void ignoresStaticSystemTimeCall_whenShadowedByLocalMethod() {
      String content =
          """
          import static java.lang.System.*;
          class Test {
              long nanoTime() {
                  return 1L;
              }
              void test() {
                  long v = nanoTime();
              }
          }
          """;

      TimeRule rule = new TimeRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }
  }

  @Nested
  class SleepRuleTests {

    @Test
    void exposesRuleIdAndDefaultSeverity() {
      SleepRule rule = new SleepRule();

      assertEquals(RuleId.SLEEP, rule.getRuleId());
      assertEquals(Severity.ERROR, rule.getDefaultSeverity());
    }

    @Test
    void ignoresUnscopedSleepCall_whenNoThreadStaticImportExists() {
      String content =
          """
          class Test {
              void sleep(long millis) {}
              void test() {
                  sleep(10L);
              }
          }
          """;

      SleepRule rule = new SleepRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsSleepCall_whenThreadSleepIsStaticallyImported() {
      String content =
          """
          import static java.lang.Thread.sleep;
          class Test {
              void test() throws InterruptedException {
                  sleep(1L);
              }
          }
          """;

      SleepRule rule = new SleepRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsTimeUnitSleep_whenFullyQualifiedTypeIsUsed() {
      String content =
          """
          class Test {
              void test() throws InterruptedException {
                  java.util.concurrent.TimeUnit.SECONDS.sleep(1);
              }
          }
          """;

      SleepRule rule = new SleepRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("TimeUnit.sleep"));
    }
  }

  @Nested
  class RandomRuleTests {

    @Test
    void exposesRuleIdAndDefaultSeverity() {
      RandomRule rule = new RandomRule();

      assertEquals(RuleId.RANDOM, rule.getRuleId());
      assertEquals(Severity.WARNING, rule.getDefaultSeverity());
    }

    @Test
    void ignoresRandomUuidCall_whenStaticImportIsForDifferentMethod() {
      String content =
          """
          import static java.util.UUID.nameUUIDFromBytes;
          class Test {
              void test() {
                  randomUUID();
              }
          }
          """;

      RandomRule rule = new RandomRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsThreadLocalRandomCurrent_whenWildcardStaticImportIsUsed() {
      String content =
          """
          import static java.util.concurrent.ThreadLocalRandom.*;
          class Test {
              void test() {
                  current();
              }
          }
          """;

      RandomRule rule = new RandomRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsRandomConstructors_forMultipleSupportedTypes() {
      String content =
          """
          class Test {
              void test() {
                  new Random();
                  new SecureRandom();
              }
          }
          """;

      RandomRule rule = new RandomRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(2, findings.size());
      assertTrue(findings.stream().allMatch(finding -> finding.ruleId() == RuleId.RANDOM));
    }
  }

  @Nested
  class ReflectionRuleTests {

    @Test
    void exposesRuleIdAndDefaultSeverity() {
      ReflectionRule rule = new ReflectionRule();

      assertEquals(RuleId.REFLECTION, rule.getRuleId());
      assertEquals(Severity.ERROR, rule.getDefaultSeverity());
    }

    @Test
    void doesNotWarnOnImportOnly_whenWarnFlagIsDisabled() {
      String content =
          """
          import java.lang.reflect.Field;
          class Test {
              void test() {}
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void ignoresGetMethod_whenClassTypeIsShadowedByNonJavaLangClassImport() {
      String content =
          """
          import com.example.Class;
          class Test {
              void test(Class helper) throws Exception {
                  helper.getMethod("x");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsGetField_whenReflectionImportSuggestsReflectionContext() {
      String content =
          """
          import java.lang.reflect.Field;
          class Test {
              void test(Object target) throws Exception {
                  target.getField("value");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("Field access"));
    }
  }

  @Nested
  class OverMockRuleTests {

    @Test
    void exposesRuleIdAndDefaultSeverity() {
      OverMockRule rule = new OverMockRule();

      assertEquals(RuleId.OVER_MOCK, rule.getRuleId());
      assertEquals(Severity.WARNING, rule.getDefaultSeverity());
    }

    @Test
    void doesNotWarn_whenWeightedTotalEqualsWarnThreshold() {
      String content =
          """
          class Test {
              void test() {
                  mock(Service.class);
                  mock(Repo.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(2, 5, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void reportsWarning_whenWeightedTotalEqualsFailThreshold() {
      String content =
          """
          class Test {
              void test() {
                  mock(Service.class);
                  spy(Repo.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(0, 3, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(Severity.WARNING, findings.get(0).severity());
      assertTrue(findings.get(0).message().contains("High mock count"));
    }

    @Test
    void countsStaticMocks_onlyWhenConfigured() {
      String content =
          """
          class Test {
              void test() {
                  org.mockito.Mockito.mockStatic(Service.class);
              }
          }
          """;

      OverMockRule disabled = new OverMockRule(0, 1, false, false);
      List<BrittleFinding> disabledFindings = checkWithAst(disabled, content);
      assertTrue(disabledFindings.isEmpty());

      OverMockRule enabled = new OverMockRule(0, 1, true, false);
      List<BrittleFinding> enabledFindings = checkWithAst(enabled, content);

      assertEquals(1, enabledFindings.size());
      assertEquals(Severity.ERROR, enabledFindings.get(0).severity());
      assertTrue(enabledFindings.get(0).evidence().contains("static: 1"));
    }
  }
}
