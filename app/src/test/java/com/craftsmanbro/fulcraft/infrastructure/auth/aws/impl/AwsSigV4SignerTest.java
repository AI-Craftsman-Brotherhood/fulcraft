package com.craftsmanbro.fulcraft.infrastructure.auth.aws.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.craftsmanbro.fulcraft.infrastructure.auth.aws.model.AwsCredentials;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsSigV4SignerTest {

  private static final AwsCredentials CREDS =
      new AwsCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", null);
  private static final Instant NOW = Instant.parse("2020-01-02T03:04:05Z");

  @Test
  void awsCredentials_rejectsBlankAccessKeyOrSecret() {
    assertThrows(IllegalArgumentException.class, () -> new AwsCredentials(null, "x", null));
    assertThrows(IllegalArgumentException.class, () -> new AwsCredentials(" ", "x", null));
    assertThrows(IllegalArgumentException.class, () -> new AwsCredentials("x", null, null));
    assertThrows(IllegalArgumentException.class, () -> new AwsCredentials("x", "  ", null));
  }

  @Test
  void signHeaders_throwsWhenMethodOrUriMissing() {
    URI uri = URI.create("https://example.amazonaws.com/");

    assertThrows(
        IllegalArgumentException.class,
        () -> AwsSigV4Signer.signHeaders(null, uri, "us-east-1", "service", CREDS, "", NOW));
    assertThrows(
        IllegalArgumentException.class,
        () -> AwsSigV4Signer.signHeaders("GET", null, "us-east-1", "service", CREDS, "", NOW));
  }

  @Test
  void signHeaders_throwsWhenCredentialsMissing() {
    URI uri = URI.create("https://example.amazonaws.com/");
    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () -> AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", "service", null, "", NOW));
    assertTrue(ex.getMessage().contains("credentials"), "Should mention missing credentials");
  }

  @Test
  void signHeaders_throwsWhenRegionServiceOrNowMissing() {
    URI uri = URI.create("https://example.amazonaws.com/");

    NullPointerException regionEx =
        assertThrows(
            NullPointerException.class,
            () -> AwsSigV4Signer.signHeaders("GET", uri, null, "service", CREDS, "", NOW));
    NullPointerException serviceEx =
        assertThrows(
            NullPointerException.class,
            () -> AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", null, CREDS, "", NOW));
    NullPointerException nowEx =
        assertThrows(
            NullPointerException.class,
            () -> AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", "service", CREDS, "", null));

    assertTrue(regionEx.getMessage().contains("region"), "Should mention missing region");
    assertTrue(serviceEx.getMessage().contains("service"), "Should mention missing service");
    assertTrue(nowEx.getMessage().contains("now"), "Should mention missing now");
  }

  @Test
  void signHeaders_throwsWhenUriHostIsMissing() {
    URI relativeUri = URI.create("/relative/path");

    NullPointerException ex =
        assertThrows(
            NullPointerException.class,
            () ->
                AwsSigV4Signer.signHeaders(
                    "GET", relativeUri, "us-east-1", "service", CREDS, "", NOW));
    assertTrue(ex.getMessage().contains("host"), "Should mention missing host");
  }

  @Test
  void signHeaders_setsAmzDateAndPayloadHashAndAuthorizationFormat() {
    URI uri = URI.create("https://example.amazonaws.com/test");

    Map<String, String> headers =
        AwsSigV4Signer.signHeaders("POST", uri, "us-east-1", "service", CREDS, "hello", NOW);

    assertEquals("20200102T030405Z", headers.get("x-amz-date"));
    assertEquals(
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        headers.get("x-amz-content-sha256"));
    assertFalse(headers.containsKey("x-amz-security-token"));

    String authorization = headers.get("Authorization");
    assertNotNull(authorization);
    assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 "), "Should start with algorithm");
    assertTrue(
        authorization.contains("Credential=AKIDEXAMPLE/20200102/us-east-1/service/aws4_request"),
        "Should include credential scope");
    assertTrue(
        authorization.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"),
        "Should include signed headers");
    assertTrue(
        authorization.matches(".*Signature=[0-9a-f]{64}$"), "Signature should be lowercase hex");
  }

  @Test
  void signHeaders_includesSessionTokenWhenProvided() {
    AwsCredentials credsWithToken = new AwsCredentials("AKID", "SECRET", "SESSION_TOKEN");
    URI uri = URI.create("https://example.amazonaws.com/");

    Map<String, String> headers =
        AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", "service", credsWithToken, null, NOW);

    assertEquals("SESSION_TOKEN", headers.get("x-amz-security-token"));
    assertTrue(
        headers
            .get("Authorization")
            .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date;x-amz-security-token"));
  }

  @Test
  void signHeaders_isDeterministicAndMethodCaseInsensitive() {
    URI uri = URI.create("https://example.amazonaws.com/");

    Map<String, String> lower =
        AwsSigV4Signer.signHeaders("get", uri, "us-east-1", "service", CREDS, null, NOW);
    Map<String, String> upper =
        AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", "service", CREDS, null, NOW);

    assertEquals(lower, upper);
    assertTrue(
        lower.get("Authorization").contains("Signature="),
        "Authorization should contain signature");
  }

  @Test
  void signHeaders_treatsMissingPathAsSlash() {
    URI withoutPath = URI.create("https://example.amazonaws.com");
    URI withSlash = URI.create("https://example.amazonaws.com/");

    Map<String, String> a =
        AwsSigV4Signer.signHeaders("GET", withoutPath, "us-east-1", "service", CREDS, null, NOW);
    Map<String, String> b =
        AwsSigV4Signer.signHeaders("GET", withSlash, "us-east-1", "service", CREDS, null, NOW);

    assertEquals(a, b);
  }

  @Test
  void signHeaders_isStableWhenQueryOrderingChanges() {
    URI aThenB = URI.create("https://example.amazonaws.com/?a=1&b=2");
    URI bThenA = URI.create("https://example.amazonaws.com/?b=2&a=1");

    String auth1 =
        AwsSigV4Signer.signHeaders("GET", aThenB, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");
    String auth2 =
        AwsSigV4Signer.signHeaders("GET", bThenA, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");

    assertEquals(auth1, auth2);
  }

  @Test
  void signHeaders_changesWhenPayloadChanges() {
    URI uri = URI.create("https://example.amazonaws.com/");

    String auth1 =
        AwsSigV4Signer.signHeaders("POST", uri, "us-east-1", "service", CREDS, "a", NOW)
            .get("Authorization");
    String auth2 =
        AwsSigV4Signer.signHeaders("POST", uri, "us-east-1", "service", CREDS, "b", NOW)
            .get("Authorization");

    assertNotEquals(auth1, auth2);
  }

  @Test
  void signHeaders_usesEmptyStringHashForNullOrEmptyPayload() {
    URI uri = URI.create("https://example.amazonaws.com/");

    Map<String, String> nullPayload =
        AwsSigV4Signer.signHeaders("POST", uri, "us-east-1", "service", CREDS, null, NOW);
    Map<String, String> emptyPayload =
        AwsSigV4Signer.signHeaders("POST", uri, "us-east-1", "service", CREDS, "", NOW);

    assertEquals(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        nullPayload.get("x-amz-content-sha256"));
    assertEquals(nullPayload, emptyPayload);
  }

  @Test
  void signHeaders_normalizesQueryEncoding() {
    URI encoded = URI.create("https://example.amazonaws.com/?a=1%2F2");
    URI raw = URI.create("https://example.amazonaws.com/?a=1/2");

    String auth1 =
        AwsSigV4Signer.signHeaders("GET", encoded, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");
    String auth2 =
        AwsSigV4Signer.signHeaders("GET", raw, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");

    assertEquals(auth1, auth2);
  }

  @Test
  void signHeaders_supportsUtf8CharactersInRawPathAndQuery() {
    URI raw = URI.create("https://example.amazonaws.com/あ?x=あ");
    URI encoded = URI.create("https://example.amazonaws.com/%E3%81%82?x=%E3%81%82");

    Map<String, String> rawHeaders =
        AwsSigV4Signer.signHeaders("GET", raw, "us-east-1", "service", CREDS, null, NOW);
    Map<String, String> encodedHeaders =
        AwsSigV4Signer.signHeaders("GET", encoded, "us-east-1", "service", CREDS, null, NOW);

    assertEquals(encodedHeaders, rawHeaders);
  }

  @Test
  void signHeaders_supportsSupplementaryUnicodeCharactersInRawPathAndQuery() {
    URI raw = URI.create("https://example.amazonaws.com/😀?x=😀");
    URI encoded = URI.create("https://example.amazonaws.com/%F0%9F%98%80?x=%F0%9F%98%80");

    Map<String, String> rawHeaders =
        AwsSigV4Signer.signHeaders("GET", raw, "us-east-1", "service", CREDS, null, NOW);
    Map<String, String> encodedHeaders =
        AwsSigV4Signer.signHeaders("GET", encoded, "us-east-1", "service", CREDS, null, NOW);

    assertEquals(encodedHeaders, rawHeaders);
  }

  @Test
  void signHeaders_treatsQueryWithoutValueAsEmptyValue() {
    URI withoutEquals = URI.create("https://example.amazonaws.com/?flag");
    URI withEquals = URI.create("https://example.amazonaws.com/?flag=");

    String auth1 =
        AwsSigV4Signer.signHeaders("GET", withoutEquals, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");
    String auth2 =
        AwsSigV4Signer.signHeaders("GET", withEquals, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");

    assertEquals(auth1, auth2);
  }

  @Test
  void signHeaders_includesPortWhenNonDefault() {
    URI defaultPort = URI.create("https://example.amazonaws.com/");
    URI customPort = URI.create("https://example.amazonaws.com:8443/");

    String auth1 =
        AwsSigV4Signer.signHeaders("GET", defaultPort, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");
    String auth2 =
        AwsSigV4Signer.signHeaders("GET", customPort, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");

    assertNotEquals(auth1, auth2);
  }

  @Test
  void signHeaders_ignoresExplicitDefaultPorts() {
    URI httpsImplicit = URI.create("https://example.amazonaws.com/");
    URI httpsExplicit = URI.create("https://example.amazonaws.com:443/");

    String httpsAuthImplicit =
        AwsSigV4Signer.signHeaders("GET", httpsImplicit, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");
    String httpsAuthExplicit =
        AwsSigV4Signer.signHeaders("GET", httpsExplicit, "us-east-1", "service", CREDS, null, NOW)
            .get("Authorization");

    assertEquals(httpsAuthImplicit, httpsAuthExplicit);
  }

  @Test
  void signHeaders_omitsSessionTokenWhenBlank() {
    AwsCredentials credsWithBlankToken =
        new AwsCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "   ");
    URI uri = URI.create("https://example.amazonaws.com/");

    Map<String, String> withBlankToken =
        AwsSigV4Signer.signHeaders(
            "GET", uri, "us-east-1", "service", credsWithBlankToken, null, NOW);
    Map<String, String> withNoToken =
        AwsSigV4Signer.signHeaders("GET", uri, "us-east-1", "service", CREDS, null, NOW);

    assertFalse(withBlankToken.containsKey("x-amz-security-token"));
    assertEquals(withNoToken, withBlankToken);
  }
}
