package com.craftsmanbro.fulcraft.infrastructure.llm.impl.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PromptSafetyTest {

  @Test
  void escapeTemplateDelimiters_handlesNullAndEmpty() {
    assertNull(PromptSafety.escapeTemplateDelimiters(null));
    assertEquals("", PromptSafety.escapeTemplateDelimiters(""));
  }

  @Test
  void escapeTemplateDelimiters_escapesCurlyPairs() {
    String input = "Hello {{name}}!";
    String expected = "Hello { {name} }!";

    assertEquals(expected, PromptSafety.escapeTemplateDelimiters(input));
  }

  @Test
  void escapeTemplateDelimiters_returnsInputWhenNoTemplateDelimiters() {
    String input = "Plain text only.";

    assertEquals(input, PromptSafety.escapeTemplateDelimiters(input));
  }

  @Test
  void wrapUntrusted_addsMarkersAndNeutralizesNestedMarkers() {
    String content = "A [UNTRUSTED_CONTENT - X] B [/UNTRUSTED_CONTENT]";
    String wrapped = PromptSafety.wrapUntrusted("SOURCE", content);

    assertTrue(wrapped.startsWith("[UNTRUSTED_CONTENT - source]"));
    assertTrue(wrapped.contains("[ UNTRUSTED_CONTENT - X]"));
    assertTrue(wrapped.contains("[/ UNTRUSTED_CONTENT]"));
    assertTrue(wrapped.endsWith("[/UNTRUSTED_CONTENT]"));
  }

  @Test
  void wrapUntrusted_handlesNullContent() {
    assertEquals("", PromptSafety.wrapUntrusted("label", null));
  }

  @Test
  void wrapUntrusted_handlesEmptyContent() {
    assertEquals("", PromptSafety.wrapUntrusted("label", ""));
  }

  @Test
  void wrapUntrusted_usesDefaultLabelWhenNull() {
    String wrapped = PromptSafety.wrapUntrusted(null, "payload");

    assertTrue(wrapped.startsWith("[UNTRUSTED_CONTENT - content]"));
  }

  @Test
  void addUntrustedPolicyIfNeeded_prependsPolicyOnce() {
    String prompt = PromptSafety.wrapUntrusted("content", "value");

    String withPolicy = PromptSafety.addUntrustedPolicyIfNeeded(prompt);
    assertTrue(withPolicy.startsWith("UNTRUSTED CONTENT POLICY:"));

    String again = PromptSafety.addUntrustedPolicyIfNeeded(withPolicy);
    assertEquals(withPolicy, again);
  }

  @Test
  void addUntrustedPolicyIfNeeded_returnsUnchangedWhenNoMarker() {
    String prompt = "No markers here";

    assertEquals(prompt, PromptSafety.addUntrustedPolicyIfNeeded(prompt));
  }

  @Test
  void addUntrustedPolicyIfNeeded_handlesNullAndBlankPrompt() {
    assertNull(PromptSafety.addUntrustedPolicyIfNeeded(null));
    assertEquals("   ", PromptSafety.addUntrustedPolicyIfNeeded("   "));
  }

  @Test
  void addUntrustedPolicyIfNeeded_prependsPolicyWhenUntrustedBlockAppearsInBody() {
    String prompt = "Intro line\n" + PromptSafety.wrapUntrusted("content", "value");

    String withPolicy = PromptSafety.addUntrustedPolicyIfNeeded(prompt);

    assertTrue(withPolicy.startsWith("UNTRUSTED CONTENT POLICY:"));
    assertTrue(withPolicy.endsWith(prompt));
  }
}
