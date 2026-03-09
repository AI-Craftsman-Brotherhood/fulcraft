package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.AbstractJavaParserBrittleRule;
import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules.BrittleRule;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Infrastructure component for checking brittle tests using JavaParser.
 *
 * <p>This class encapsulates the usage of the JavaParser library to parse test files and apply
 * provided {@link BrittleRule}s.
 */
public class JavaParserBrittleTestChecker {

  private final JavaParser javaParser;

  public JavaParserBrittleTestChecker() {
    final ParserConfiguration configuration = new ParserConfiguration();
    configuration.setLanguageLevel(LanguageLevel.BLEEDING_EDGE);
    this.javaParser = new JavaParser(configuration);
  }

  /**
   * Checks the provided test files against the given rules.
   *
   * @param testFiles list of test file paths to check
   * @param rules list of rules to apply
   * @param allowlistPatterns set of path patterns to exclude
   * @return list of findings
   */
  public List<BrittleFinding> check(
      final List<Path> testFiles,
      final List<BrittleRule> rules,
      final Set<String> allowlistPatterns) {
    final List<BrittleFinding> allFindings = new ArrayList<>();
    if (testFiles.isEmpty()) {
      return allFindings;
    }
    Logger.info(
        "Checking " + testFiles.size() + " generated test file(s) for brittle patterns (AST mode)");
    for (final Path filePath : testFiles) {
      if (Files.exists(filePath)) {
        if (isAllowlisted(filePath, allowlistPatterns)) {
          Logger.debug(
              com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                  "infra.common.log.message", "Skipping allowlisted file: " + filePath));
        } else {
          try {
            final List<BrittleFinding> fileFindings = checkFileWithAst(filePath, rules);
            allFindings.addAll(fileFindings);
          } catch (IOException e) {
            Logger.error(
                com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                    "infra.common.log.message",
                    "Failed to read file " + filePath + ": " + e.getMessage()));
          } catch (ParseProblemException e) {
            Logger.warn(
                "Failed to parse file " + filePath + " (may not be valid Java): " + e.getMessage());
          }
        }
      } else {
        Logger.warn(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Generated test file not found: " + filePath));
      }
    }
    return allFindings;
  }

  private List<BrittleFinding> checkFileWithAst(final Path filePath, final List<BrittleRule> rules)
      throws IOException {
    final String content = Files.readString(filePath);
    final String pathString = filePath.toString();
    final List<String> lines = content.lines().toList();
    CompilationUnit cu = null;
    final boolean hasAstRule =
        rules.stream().anyMatch(AbstractJavaParserBrittleRule.class::isInstance);
    if (hasAstRule) {
      final ParseResult<CompilationUnit> result = javaParser.parse(content);
      final var parsedResult = result.getResult();
      if (result.isSuccessful() && parsedResult.isPresent()) {
        cu = parsedResult.get();
      } else {
        throw new ParseProblemException(result.getProblems());
      }
    }
    final List<BrittleFinding> findings = new ArrayList<>();
    for (final BrittleRule rule : rules) {
      if (rule.isEnabled()) {
        if (rule instanceof AbstractJavaParserBrittleRule astRule) {
          findings.addAll(astRule.checkAst(cu, pathString));
        } else {
          findings.addAll(rule.check(pathString, content, lines));
        }
      }
    }
    return findings;
  }

  private boolean isAllowlisted(final Path filePath, final Set<String> allowlistPatterns) {
    if (allowlistPatterns == null || allowlistPatterns.isEmpty()) {
      return false;
    }
    final String pathString = filePath.toString();
    return allowlistPatterns.stream()
        .filter(pattern -> pattern != null && !pattern.isBlank())
        .anyMatch(pathString::contains);
  }
}
