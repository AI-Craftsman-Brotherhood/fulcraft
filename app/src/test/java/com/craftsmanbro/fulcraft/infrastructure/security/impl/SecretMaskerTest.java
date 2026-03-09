package com.craftsmanbro.fulcraft.infrastructure.security.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.security.contract.SecretMaskingPort;
import com.craftsmanbro.fulcraft.infrastructure.security.model.MaskedText;
import org.junit.jupiter.api.Test;

class SecretMaskerTest {

  @Test
  void mask_returnsInputForNullOrEmpty() {
    assertNull(SecretMasker.mask(null));
    assertEquals("", SecretMasker.mask(""));
  }

  @Test
  void mask_replacesCommonSecretPatterns() {
    String pem = "-----BEGIN PRIVATE KEY-----\nABCDEF123456\n-----END PRIVATE KEY-----";
    String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.signature";
    String longToken = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6";
    String hexToken = "0123456789abcdef0123456789abcdef";
    String base64Token = "QWxhZGRpbjpvcGVuIHNlc2FtZSB0b2tlbg==";
    String input =
        "api_key=abc123 token:\"tok_456\" Authorization: Bearer "
            + jwt
            + " x-api-key="
            + longToken
            + "\n"
            + "access_token="
            + hexToken
            + "\n"
            + "Authorization: AWS4-HMAC-SHA256 "
            + base64Token
            + "\n"
            + "\"Authorization\": \"Bearer short.jwt.token\""
            + "\n"
            + pem;

    String masked = SecretMasker.mask(input);

    assertFalse(masked.contains("abc123"), "api_key should be masked");
    assertFalse(masked.contains("tok_456"), "token should be masked");
    assertFalse(masked.contains(jwt), "JWT should be masked");
    assertFalse(masked.contains(longToken), "long token should be masked");
    assertFalse(masked.contains(hexToken), "hex token should be masked");
    assertFalse(masked.contains(base64Token), "base64 token should be masked");
    assertFalse(masked.contains("short.jwt.token"), "authorization JSON token should be masked");
    assertFalse(masked.contains(pem), "PEM block should be masked");
    assertTrue(masked.contains("****"), "masked output should include placeholder");
  }

  @Test
  void mask_doesNotAlterNormalText() {
    String input = "Processing completed successfully. token count is 5.";
    assertEquals(input, SecretMasker.mask(input));
  }

  @Test
  void mask_doesNotMaskConfigLikeDottedPaths() {
    String input =
        "analysis.preprocess.clean_work_dir - Clean preprocess directory\n"
            + "selection_rules.complexity.strategy - Complexity strategy\n"
            + "governance.redaction.allowlist_path - Redaction allowlist path";

    assertEquals(input, SecretMasker.mask(input));
  }

  @Test
  void mask_preservesAuthorizationSchemeWhileMaskingToken() {
    String input = "Authorization: Bearer aaaaaaaa.bbbbbbbb.cccccccc";
    String masked = SecretMasker.mask(input);
    assertEquals("Authorization: Bearer ****", masked);
  }

  @Test
  void mask_preservesQuotesForAuthorizationHeader() {
    String input = "\"Authorization\": \"Bearer aaaaaaaa.bbbbbbbb.cccccccc\"";
    String masked = SecretMasker.mask(input);
    assertEquals("\"Authorization\": \"Bearer ****\"", masked);
  }

  @Test
  void mask_preservesQuotesForKeyValuePairs() {
    String input = "password=\"secret\" token=plain";
    String masked = SecretMasker.mask(input);
    assertEquals("password=\"****\" token=****", masked);
  }

  @Test
  void mask_masksQuotedJsonLikeSecretKeys() {
    String input = "{\"token\":\"abc123\",\"password\":\"pw456\",\"x-api-key\":\"z9y8x7\"}";
    String masked = SecretMasker.mask(input);

    assertEquals("{\"token\":\"****\",\"password\":\"****\",\"x-api-key\":\"****\"}", masked);
    assertFalse(masked.contains("abc123"));
    assertFalse(masked.contains("pw456"));
    assertFalse(masked.contains("z9y8x7"));
  }

  @Test
  void mask_masksAuthorizationWithEqualsAndMixedCase() {
    String input = "authorization = basic dXNlcjpwYXNz";
    String masked = SecretMasker.mask(input);
    assertEquals("authorization = basic ****", masked);
  }

  @Test
  void maskStackTrace_masksExceptionDetails() {
    RuntimeException ex = new RuntimeException("api_key=abc123");
    String masked = SecretMasker.maskStackTrace(ex);
    assertFalse(masked.contains("abc123"));
    assertTrue(masked.contains("****"));
  }

  @Test
  void maskStackTrace_returnsEmptyWhenNull() {
    assertEquals("", SecretMasker.maskStackTrace(null));
  }

  @Test
  void port_exposesContractAndModel() {
    SecretMaskingPort port = SecretMasker.port();

    assertEquals("token=****", port.maskText("token=abc123"));

    MaskedText masked = port.maskValue("password=secret");
    assertEquals("password=secret", masked.original());
    assertEquals("password=****", masked.masked());
    assertTrue(masked.changed());
  }
}
