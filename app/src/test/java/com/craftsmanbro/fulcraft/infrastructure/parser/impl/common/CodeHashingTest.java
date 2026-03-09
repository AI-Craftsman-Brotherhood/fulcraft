package com.craftsmanbro.fulcraft.infrastructure.parser.impl.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CodeHashingTest {

  @Test
  void hashNormalized_shouldIgnoreWhitespaceOutsideLiterals() {
    String spaced = "{ int a = 1; return a + 2; }";
    String compact = "{int a=1;return a+2;}";

    assertThat(CodeHashing.hashNormalized(spaced)).isEqualTo(CodeHashing.hashNormalized(compact));
  }

  @Test
  void hashNormalized_shouldPreserveWhitespaceInsideStringLiterals() {
    String withSpace = "return \"a b\";";
    String withoutSpace = "return \"ab\";";

    assertThat(CodeHashing.hashNormalized(withSpace))
        .isNotEqualTo(CodeHashing.hashNormalized(withoutSpace));
  }

  @Test
  void hashNormalized_shouldIgnoreWhitespaceInsideCommentsWithQuotes() {
    String withSpaces = "int a=0; // \"quoted\" comment \n return a;";
    String withoutSpaces = "int a=0;//\"quoted\"comment\nreturn a;";

    assertThat(CodeHashing.hashNormalized(withSpaces))
        .isEqualTo(CodeHashing.hashNormalized(withoutSpaces));
  }

  @Test
  void hashNormalized_shouldPreserveWhitespaceInsideTextBlocks() {
    String withSpace = "String value = \"\"\"a b\"\"\";";
    String withoutSpace = "String value = \"\"\"ab\"\"\";";

    assertThat(CodeHashing.hashNormalized(withSpace))
        .isNotEqualTo(CodeHashing.hashNormalized(withoutSpace));
  }

  @Test
  void hashNormalized_shouldHandleEscapedQuotesAndCharLiterals() {
    String spaced = "return \"a\\\" b\" + ' ';";
    String compact = "return\"a\\\" b\"+' ';";

    assertThat(CodeHashing.hashNormalized(spaced)).isEqualTo(CodeHashing.hashNormalized(compact));
  }
}
