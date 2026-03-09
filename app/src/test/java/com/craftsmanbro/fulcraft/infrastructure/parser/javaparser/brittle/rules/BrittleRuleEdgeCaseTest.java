package com.craftsmanbro.fulcraft.infrastructure.parser.javaparser.brittle.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.AbstractJavaParserBrittleRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.OverMockRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.RandomRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.ReflectionRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.SleepRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.TimeRule;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrittleRuleEdgeCaseTest {

  private static List<BrittleFinding> checkWithAst(
      AbstractJavaParserBrittleRule rule, String content) {
    CompilationUnit cu = StaticJavaParser.parse(content);
    return rule.checkAst(cu, "Test.java");
  }

  @Nested
  class TimeRuleEdgeCases {

    private TimeRule rule;

    @BeforeEach
    void setUp() {
      rule = new TimeRule();
    }

    @Test
    void detectsStaticImportCurrentTimeMillis_whenCalledDirectly() {
      String content =
          """
          import static java.lang.System.currentTimeMillis;
          class Test {
              void test() {
                  long value = currentTimeMillis();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.TIME, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void ignoresStaticImportCurrentTimeMillis_whenLocalMethodShadows() {
      String content =
          """
          import static java.lang.System.currentTimeMillis;
          class Test {
              long currentTimeMillis() {
                  return 1L;
              }
              void test() {
                  long value = currentTimeMillis();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty(), "Local method should shadow static import");
    }

    @Test
    void detectsStaticImportCurrentTimeMillis_whenOnlyNestedTypeDefinesSameMethodName() {
      String content =
          """
          import static java.lang.System.currentTimeMillis;
          class Test {
              static final class Helper {
                  static long currentTimeMillis() {
                      return 1L;
                  }
              }
              void test() {
                  long value = currentTimeMillis();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.TIME, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsStaticImportCurrentTimeMillis_whenLocalMethodHasDifferentArity() {
      String content =
          """
          import static java.lang.System.currentTimeMillis;
          class Test {
              long currentTimeMillis(String source) {
                  return 1L;
              }
              void test() {
                  long value = currentTimeMillis();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.TIME, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsClockSystemUtc_whenDirectCall() {
      String content =
          """
          class Test {
              void test() {
                  Clock.systemUTC();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.TIME, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }
  }

  @Nested
  class SleepRuleEdgeCases {

    private SleepRule rule;

    @BeforeEach
    void setUp() {
      rule = new SleepRule();
    }

    @Test
    void detectsCurrentThreadSleep_whenStaticImportCurrentThread() {
      String content =
          """
          import static java.lang.Thread.currentThread;
          class Test {
              void test() throws InterruptedException {
                  currentThread().sleep(10);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
    }

    @Test
    void ignoresCurrentThreadSleep_whenLocalMethodShadowsStaticImport() {
      String content =
          """
          import static java.lang.Thread.currentThread;
          class Test {
              Helper currentThread() {
                  return new Helper();
              }
              void test() {
                  currentThread().sleep(10);
              }
              static final class Helper {
                  void sleep(long millis) {}
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsThreadSleep_whenOnlyNestedTypeDefinesSameMethodName() {
      String content =
          """
          import static java.lang.Thread.sleep;
          class Test {
              static final class Helper {
                  static void sleep(long millis) {}
              }
              void test() throws InterruptedException {
                  sleep(10L);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsThreadSleep_whenLocalMethodHasDifferentArity() {
      String content =
          """
          import static java.lang.Thread.sleep;
          class Test {
              void sleep(long millis, String reason) {}
              void test() throws InterruptedException {
                  sleep(10L);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsThreadSleep_whenLocalMethodHasIncompatibleParameterType() {
      String content =
          """
          import static java.lang.Thread.sleep;
          class Test {
              void sleep(String millis) {}
              void test() throws InterruptedException {
                  sleep(10L);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsThreadSleep_whenStaticImportAsterisk() {
      String content =
          """
          import static java.lang.Thread.*;
          class Test {
              void test() throws InterruptedException {
                  sleep(5);
                  currentThread().sleep(10);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(2, findings.size());
      assertTrue(findings.stream().allMatch(finding -> finding.ruleId() == RuleId.SLEEP));
    }

    @Test
    void detectsTimeUnitSleep_whenStaticImportConstant() {
      String content =
          """
          import static java.util.concurrent.TimeUnit.SECONDS;
          class Test {
              void test() throws InterruptedException {
                  SECONDS.sleep(1);
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.SLEEP, findings.get(0).ruleId());
    }
  }

  @Nested
  class RandomRuleEdgeCases {

    private RandomRule rule;

    @BeforeEach
    void setUp() {
      rule = new RandomRule();
    }

    @Test
    void detectsStaticImportUuidRandomUuid_whenCalledDirectly() {
      String content =
          """
          import static java.util.UUID.randomUUID;
          class Test {
              void test() {
                  randomUUID();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void ignoresStaticImportRandomUuid_whenLocalMethodShadowsIt() {
      String content =
          """
          import static java.util.UUID.randomUUID;
          class Test {
              String randomUUID() {
                  return "fixed";
              }
              void test() {
                  randomUUID();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsStaticImportRandomUuid_whenOnlyNestedTypeDefinesSameMethodName() {
      String content =
          """
          import static java.util.UUID.randomUUID;
          class Test {
              static final class Helper {
                  static String randomUUID() {
                      return "fixed";
                  }
              }
              void test() {
                  randomUUID();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsStaticImportRandomUuid_whenLocalMethodHasDifferentArity() {
      String content =
          """
          import static java.util.UUID.randomUUID;
          class Test {
              String randomUUID(String seed) {
                  return seed;
              }
              void test() {
                  randomUUID();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsStaticImportThreadLocalRandomCurrent_whenCalledDirectly() {
      String content =
          """
          import static java.util.concurrent.ThreadLocalRandom.current;
          class Test {
              void test() {
                  current();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void detectsNewSplittableRandom_whenConstructed() {
      String content =
          """
          class Test {
              void test() {
                  new SplittableRandom();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }

    @Test
    void detectsStaticImportAsterisk_whenRandomUuidCalled() {
      String content =
          """
          import static java.util.UUID.*;
          class Test {
              void test() {
                  randomUUID();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.RANDOM, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("static import"));
    }

    @Test
    void ignoresRandomCall_withoutStaticImport() {
      String content =
          """
          class Test {
              void test() {
                  random();
              }
          }
          """;

      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty(), "random() without static import should be ignored");
    }
  }

  @Nested
  class ReflectionRuleEdgeCases {

    @Test
    void warnsOnReflectionImport_whenWarnOnImportOnlyEnabled() {
      String content =
          """
          import java.lang.reflect.Field;
          class Test {
              void test() {
                  int value = 1;
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule(Severity.ERROR, true);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
      assertTrue(findings.get(0).message().contains("Reflection import"));
    }

    @Test
    void detectsForName_whenStaticImportClassForName() {
      String content =
          """
          import static java.lang.Class.forName;
          class Test {
              void test() throws Exception {
                  forName("com.example.Foo");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
    }

    @Test
    void ignoresForName_whenLocalMethodShadowsStaticImport() {
      String content =
          """
          import static java.lang.Class.forName;
          class Test {
              Class<?> forName(String name) {
                  return Test.class;
              }
              void test() throws Exception {
                  forName("com.example.Foo");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void detectsForName_whenOnlyNestedTypeDefinesSameMethodName() {
      String content =
          """
          import static java.lang.Class.forName;
          class Test {
              static final class Helper {
                  static Class<?> forName(String name) {
                      return Helper.class;
                  }
              }
              void test() throws Exception {
                  forName("com.example.Foo");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
    }

    @Test
    void detectsForName_whenLocalMethodHasDifferentArity() {
      String content =
          """
          import static java.lang.Class.forName;
          class Test {
              Class<?> forName(String name, ClassLoader loader) {
                  return Test.class;
              }
              void test() throws Exception {
                  forName("com.example.Foo");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
    }

    @Test
    void detectsForName_whenLocalMethodHasIncompatibleParameterType() {
      String content =
          """
          import static java.lang.Class.forName;
          class Test {
              Class<?> forName(int typeId) {
                  return Test.class;
              }
              void test() throws Exception {
                  forName("com.example.Foo");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
    }

    @Test
    void detectsGetMethod_whenCalledViaGetClassScope() {
      String content =
          """
          class Test {
              void test() throws Exception {
                  Object target = new Object();
                  target.getClass().getMethod("toString");
              }
          }
          """;

      ReflectionRule rule = new ReflectionRule();
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.REFLECTION, findings.get(0).ruleId());
      assertTrue(findings.get(0).message().contains("Method access"));
    }
  }

  @Nested
  class OverMockRuleEdgeCases {

    @Test
    void countsStubs_whenEnabled() {
      String content =
          """
          class Test {
              void test() {
                  new WireMockRule();
              }
          }
          """;

      OverMockRule disabled = new OverMockRule(0, 2, false, false);
      assertTrue(checkWithAst(disabled, content).isEmpty());

      OverMockRule enabled = new OverMockRule(0, 2, false, true);
      List<BrittleFinding> findings = checkWithAst(enabled, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.OVER_MOCK, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
      assertTrue(findings.get(0).evidence().contains("stub: 1"));
    }

    @Test
    void ignoresMockCall_whenLocalHelperShadowsMockitoMethod() {
      String content =
          """
          class Test {
              void mock(Class<?> type) {}
              void test() {
                  mock(Service.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(0, 1, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertTrue(findings.isEmpty());
    }

    @Test
    void countsMockCall_whenOnlyNestedTypeDefinesSameMethodName() {
      String content =
          """
          class Test {
              static final class Helper {
                  static void mock(Class<?> type) {}
              }
              void test() {
                  mock(Service.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(0, 2, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.OVER_MOCK, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }

    @Test
    void countsMockCall_whenLocalMethodHasDifferentArity() {
      String content =
          """
          class Test {
              void mock(Class<?> type, String label) {}
              void test() {
                  mock(Service.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(0, 2, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.OVER_MOCK, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }

    @Test
    void countsMockCall_whenLocalMethodHasIncompatibleParameterType() {
      String content =
          """
          class Test {
              void mock(String className) {}
              void test() {
                  mock(Service.class);
              }
          }
          """;

      OverMockRule rule = new OverMockRule(0, 2, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.OVER_MOCK, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }

    @Test
    void countsMockAnnotations_onMethodParameters() {
      String content =
          """
          import org.mockito.Mock;
          import org.mockito.Spy;
          class Test {
              void test(@Mock Service service, @Spy Repo repo) {
              }
          }
          """;

      OverMockRule rule = new OverMockRule(1, 5, false, false);
      List<BrittleFinding> findings = checkWithAst(rule, content);

      assertEquals(1, findings.size());
      assertEquals(RuleId.OVER_MOCK, findings.get(0).ruleId());
      assertEquals(Severity.WARNING, findings.get(0).severity());
    }
  }
}
