package com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.rules;

import com.craftsmanbro.fulcraft.infrastructure.parser.impl.javaparser.brittle.model.BrittleFinding;
import java.util.List;

/**
 * Interface for brittle test detection rules.
 *
 * <p>Each rule scans test file content and returns any findings. Rules can be text-based
 * (regex/pattern matching) or AST-based (using JavaParser/Spoon). AST rules should extend {@link
 * AbstractJavaParserBrittleRule}; the adapter will call {@code checkAst} for those, while
 * text-based rules should implement {@link #check(String, String, List)} directly.
 */
public interface BrittleRule {

  /**
   * Get the rule identifier.
   *
   * @return The rule ID
   */
  BrittleFinding.RuleId getRuleId();

  /**
   * Get the default severity for this rule.
   *
   * @return The default severity
   */
  BrittleFinding.Severity getDefaultSeverity();

  /**
   * Check the given file content for violations.
   *
   * <p>Text-based rules should implement this. AST-based rules should extend {@link
   * AbstractJavaParserBrittleRule} and implement {@code checkAst} instead.
   *
   * @param filePath The path to the file being checked
   * @param content The file content as a string
   * @param lines The file content split into lines (for line number calculation)
   * @return A list of findings (empty if no violations found)
   */
  List<BrittleFinding> check(String filePath, String content, List<String> lines);

  /**
   * Check if this rule is enabled based on configuration.
   *
   * @return true if the rule should be applied
   */
  default boolean isEnabled() {
    return true;
  }
}
