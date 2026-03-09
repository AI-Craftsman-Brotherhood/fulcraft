package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.RuleId;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding.Severity;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.AbstractJavaParserBrittleRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.BrittleRule;
import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaParserBrittleTestCheckerTest {

  @TempDir Path tempDir;

  @Test
  void check_appliesAstAndTextRules() throws Exception {
    Path file = tempDir.resolve("GeneratedTest.java");
    Files.writeString(file, "class GeneratedTest { void t() { doThing(); } }");

    AtomicBoolean astInvoked = new AtomicBoolean(false);
    AtomicBoolean textInvoked = new AtomicBoolean(false);

    BrittleRule astRule = new TrackingAstRule(astInvoked);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings =
        checker.check(List.of(file), List.of(astRule, textRule), Set.of());

    assertEquals(2, findings.size());
    assertTrue(astInvoked.get());
    assertTrue(textInvoked.get());
  }

  @Test
  void check_skipsAllowlistedFiles() throws Exception {
    Path file = tempDir.resolve("AllowlistedTest.java");
    Files.writeString(file, "class AllowlistedTest { void t() {} }");

    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings =
        checker.check(List.of(file), List.of(textRule), Set.of("Allowlisted"));

    assertEquals(0, findings.size());
    assertFalse(textInvoked.get());
  }

  @Test
  void check_returnsEmptyWhenNoFilesProvided() {
    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings = checker.check(List.of(), List.of(textRule), Set.of());

    assertTrue(findings.isEmpty());
    assertFalse(textInvoked.get());
  }

  @Test
  void check_ignoresMissingFiles() {
    Path missing = tempDir.resolve("MissingTest.java");

    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings = checker.check(List.of(missing), List.of(textRule), Set.of());

    assertTrue(findings.isEmpty());
    assertFalse(textInvoked.get());
  }

  @Test
  void check_handlesIoExceptionWhenPathIsDirectory() throws Exception {
    Path directoryPath = tempDir.resolve("DirectoryInsteadOfFile.java");
    Files.createDirectories(directoryPath);

    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings =
        checker.check(List.of(directoryPath), List.of(textRule), Set.of());

    assertTrue(findings.isEmpty());
    assertFalse(textInvoked.get());
  }

  @Test
  void check_handlesParseProblemForAstRules() throws Exception {
    Path file = tempDir.resolve("BrokenTest.java");
    Files.writeString(file, "class BrokenTest { void t( }");

    AtomicBoolean astInvoked = new AtomicBoolean(false);
    BrittleRule astRule = new TrackingAstRule(astInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings = checker.check(List.of(file), List.of(astRule), Set.of());

    assertTrue(findings.isEmpty());
    assertFalse(astInvoked.get());
  }

  @Test
  void check_doesNotInvokeDisabledRules() throws Exception {
    Path file = tempDir.resolve("DisabledRuleTest.java");
    Files.writeString(file, "class DisabledRuleTest { void t() {} }");

    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule disabledRule = new TrackingTextRule(textInvoked, false);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings = checker.check(List.of(file), List.of(disabledRule), Set.of());

    assertTrue(findings.isEmpty());
    assertFalse(textInvoked.get());
  }

  @Test
  void check_ignoresNullAndBlankAllowlistPatterns() throws Exception {
    Path file = tempDir.resolve("AllowlistPatternTest.java");
    Files.writeString(file, "class AllowlistPatternTest { void t() {} }");

    AtomicBoolean textInvoked = new AtomicBoolean(false);
    BrittleRule textRule = new TrackingTextRule(textInvoked);

    JavaParserBrittleTestChecker checker = new JavaParserBrittleTestChecker();
    List<BrittleFinding> findings =
        checker.check(
            List.of(file),
            List.of(textRule),
            new java.util.HashSet<>(java.util.Arrays.asList(null, "", "   ", "NotMatched")));

    assertEquals(1, findings.size());
    assertTrue(textInvoked.get());
  }

  private static final class TrackingAstRule extends AbstractJavaParserBrittleRule {
    private final AtomicBoolean invoked;

    private TrackingAstRule(AtomicBoolean invoked) {
      super(Severity.WARNING);
      this.invoked = invoked;
    }

    @Override
    public RuleId getRuleId() {
      return RuleId.TIME;
    }

    @Override
    public List<BrittleFinding> checkAst(CompilationUnit cu, String filePath) {
      if (cu == null) {
        throw new IllegalStateException("CompilationUnit is required");
      }
      invoked.set(true);
      return List.of(
          new BrittleFinding(getRuleId(), getDefaultSeverity(), filePath, -1, "ast", "AST"));
    }
  }

  private static final class TrackingTextRule implements BrittleRule {
    private final AtomicBoolean invoked;
    private final boolean enabled;

    private TrackingTextRule(AtomicBoolean invoked) {
      this(invoked, true);
    }

    private TrackingTextRule(AtomicBoolean invoked, boolean enabled) {
      this.invoked = invoked;
      this.enabled = enabled;
    }

    @Override
    public RuleId getRuleId() {
      return RuleId.RANDOM;
    }

    @Override
    public Severity getDefaultSeverity() {
      return Severity.WARNING;
    }

    @Override
    public List<BrittleFinding> check(String filePath, String content, List<String> lines) {
      invoked.set(true);
      return List.of(
          new BrittleFinding(getRuleId(), getDefaultSeverity(), filePath, 1, "text", "TEXT"));
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }
  }
}
