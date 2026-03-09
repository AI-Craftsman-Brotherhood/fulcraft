package com.craftsmanbro.fulcraft.infrastructure.auth.aws.impl;

import com.craftsmanbro.fulcraft.infrastructure.auth.aws.contract.AwsRequestSigningPort;
import com.craftsmanbro.fulcraft.infrastructure.auth.aws.model.AwsCredentials;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Minimal AWS SigV4 signer for HTTP requests. */
public final class AwsSigV4Signer implements AwsRequestSigningPort {

  private static final DateTimeFormatter AMZ_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private static final DateTimeFormatter DATE_STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private static final String HASH_ALGORITHM = "SHA-256";

  private static final String AWS4_HMAC_ALGORITHM = "AWS4-HMAC-SHA256";

  private static final String AWS4_REQUEST = "aws4_request";

  private static final String SCHEME_PREFIX = "AWS4";

  private static final char URI_PATH_SEPARATOR = '/';

  private static final String URI_PATH_SEPARATOR_STRING = String.valueOf(URI_PATH_SEPARATOR);

  private static final String X_AMZ_DATE = "x-amz-date";

  private static final String X_AMZ_CONTENT_SHA256 = "x-amz-content-sha256";

  private static final String X_AMZ_SECURITY_TOKEN = "x-amz-security-token";

  private static final String AUTHORIZATION = "Authorization";

  private static final java.util.regex.Pattern SPACES_PATTERN =
      java.util.regex.Pattern.compile("\\s+");

  private static final AwsSigV4Signer INSTANCE = new AwsSigV4Signer();

  private AwsSigV4Signer() {}

  public static AwsRequestSigningPort port() {
    return INSTANCE;
  }

  public static Map<String, String> signHeaders(
      final String method,
      final URI uri,
      final String region,
      final String service,
      final AwsCredentials credentials,
      final String payload,
      final Instant now) {
    return INSTANCE.sign(method, uri, region, service, credentials, payload, now);
  }

  @Override
  public Map<String, String> sign(
      final String method,
      final URI uri,
      final String region,
      final String service,
      final AwsCredentials credentials,
      final String payload,
      final Instant now) {
    if (method == null || uri == null) {
      throw new IllegalArgumentException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "method and uri are required"));
    }
    Objects.requireNonNull(
        credentials,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "credentials are required"));
    Objects.requireNonNull(
        region,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "region is required"));
    Objects.requireNonNull(
        service,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "service is required"));
    Objects.requireNonNull(
        now,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "now is required"));
    final String host = buildHostHeader(uri);
    final String amzDate = AMZ_DATE.format(now);
    final String dateStamp = DATE_STAMP.format(now);
    final String canonicalUri = canonicalUri(uri.getRawPath());
    final String canonicalQuery = canonicalQuery(uri.getRawQuery());
    final String payloadHash = sha256Hex(payload == null ? "" : payload);
    final var headers = new LinkedHashMap<String, String>();
    headers.put("host", host);
    headers.put(X_AMZ_DATE, amzDate);
    headers.put(X_AMZ_CONTENT_SHA256, payloadHash);
    if (credentials.sessionToken() != null && !credentials.sessionToken().isBlank()) {
      headers.put(X_AMZ_SECURITY_TOKEN, credentials.sessionToken());
    }
    final var sortedHeaders = new ArrayList<>(headers.entrySet());
    sortedHeaders.sort(Comparator.comparing(Map.Entry::getKey));
    final var canonicalHeaders = new StringBuilder();
    final var signedHeaders = new StringBuilder();
    for (int i = 0; i < sortedHeaders.size(); i++) {
      final Map.Entry<String, String> e = sortedHeaders.get(i);
      canonicalHeaders
          .append(e.getKey())
          .append(':')
          .append(normalizeSpaces(e.getValue()))
          .append('\n');
      if (i > 0) {
        signedHeaders.append(';');
      }
      signedHeaders.append(e.getKey());
    }
    final String canonicalRequest =
        createCanonicalRequest(
            method,
            canonicalUri,
            canonicalQuery,
            canonicalHeaders.toString(),
            signedHeaders.toString(),
            payloadHash);
    final String credentialScope = dateStamp + "/" + region + "/" + service + "/" + AWS4_REQUEST;
    final String stringToSign = createStringToSign(amzDate, credentialScope, canonicalRequest);
    final byte[] signingKey =
        getSignatureKey(credentials.secretAccessKey(), dateStamp, region, service);
    final String signature = hmacSha256Hex(signingKey, stringToSign);
    final String authorization =
        AWS4_HMAC_ALGORITHM
            + " "
            + "Credential="
            + credentials.accessKeyId()
            + "/"
            + credentialScope
            + ", "
            + "SignedHeaders="
            + signedHeaders
            + ", "
            + "Signature="
            + signature;
    final var out = new LinkedHashMap<String, String>();
    out.put(AUTHORIZATION, authorization);
    out.put(X_AMZ_DATE, amzDate);
    out.put(X_AMZ_CONTENT_SHA256, payloadHash);
    if (credentials.sessionToken() != null && !credentials.sessionToken().isBlank()) {
      out.put(X_AMZ_SECURITY_TOKEN, credentials.sessionToken());
    }
    return out;
  }

  private static String createCanonicalRequest(
      final String method,
      final String canonicalUri,
      final String canonicalQuery,
      final String canonicalHeaders,
      final String signedHeaders,
      final String payloadHash) {
    return method.toUpperCase(Locale.ROOT)
        + "\n"
        + canonicalUri
        + "\n"
        + canonicalQuery
        + "\n"
        + canonicalHeaders
        + "\n"
        + signedHeaders
        + "\n"
        + payloadHash;
  }

  private static String createStringToSign(
      final String amzDate, final String credentialScope, final String canonicalRequest) {
    return AWS4_HMAC_ALGORITHM
        + "\n"
        + amzDate
        + "\n"
        + credentialScope
        + "\n"
        + sha256Hex(canonicalRequest);
  }

  private static String canonicalUri(final String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return URI_PATH_SEPARATOR_STRING;
    }
    final String path =
        rawPath.startsWith(URI_PATH_SEPARATOR_STRING)
            ? rawPath
            : URI_PATH_SEPARATOR_STRING + rawPath;
    final String[] segments = path.split(URI_PATH_SEPARATOR_STRING, -1);
    final StringBuilder out = new StringBuilder();
    for (int i = 0; i < segments.length; i++) {
      if (i > 0) {
        out.append(URI_PATH_SEPARATOR);
      }
      out.append(awsPercentEncode(percentDecode(segments[i])));
    }
    return out.toString();
  }

  private static String canonicalQuery(final String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "";
    }
    final List<String> parts = new ArrayList<>();
    final String[] pairs = rawQuery.split("&", -1);
    for (final String pair : pairs) {
      final int idx = pair.indexOf('=');
      final String rawName = idx >= 0 ? pair.substring(0, idx) : pair;
      final String rawValue = idx >= 0 ? pair.substring(idx + 1) : "";
      final String name = awsPercentEncode(percentDecode(rawName));
      final String value = awsPercentEncode(percentDecode(rawValue));
      parts.add(name + "=" + value);
    }
    parts.sort(Comparator.naturalOrder());
    return String.join("&", parts);
  }

  private static String normalizeSpaces(final String value) {
    if (value == null) {
      return "";
    }
    return SPACES_PATTERN.matcher(value.trim()).replaceAll(" ");
  }

  private static byte[] hmacSha256(final byte[] key, final String data) {
    try {
      final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new IllegalStateException(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.error.message", "Failed to compute " + HMAC_ALGORITHM),
          e);
    }
  }

  private static String hmacSha256Hex(final byte[] key, final String data) {
    return HexFormat.of().formatHex(hmacSha256(key, data));
  }

  private static byte[] getSignatureKey(
      final String secretKey,
      final String dateStamp,
      final String regionName,
      final String serviceName) {
    final byte[] kSecret = (SCHEME_PREFIX + secretKey).getBytes(StandardCharsets.UTF_8);
    final byte[] kDate = hmacSha256(kSecret, dateStamp);
    final byte[] kRegion = hmacSha256(kDate, regionName);
    final byte[] kService = hmacSha256(kRegion, serviceName);
    return hmacSha256(kService, AWS4_REQUEST);
  }

  private static String sha256Hex(final String input) {
    try {
      final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(HASH_ALGORITHM + " not available", e);
    }
  }

  private static String buildHostHeader(final URI uri) {
    final String host =
        Objects.requireNonNull(
            uri.getHost(),
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.error.argument_null", "URI must have a host"));
    final int port = uri.getPort();
    if (port == -1 || isDefaultPort(uri.getScheme(), port)) {
      return host;
    }
    return host + ":" + port;
  }

  private static boolean isDefaultPort(final String scheme, final int port) {
    if (scheme == null) {
      return false;
    }
    return ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
  }

  private static String awsPercentEncode(final String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    final StringBuilder out = new StringBuilder(bytes.length * 3);
    for (final byte b : bytes) {
      final int c = b & 0xff;
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_'
          || c == '.'
          || c == '~') {
        out.append((char) c);
      } else {
        out.append('%');
        out.append(toHexUpper((c >> 4) & 0x0f));
        out.append(toHexUpper(c & 0x0f));
      }
    }
    return out.toString();
  }

  private static String percentDecode(final String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(raw.length());
    int i = 0;
    while (i < raw.length()) {
      final char ch = raw.charAt(i);
      if (ch == '%' && i + 2 < raw.length()) {
        final int hi = hexValue(raw.charAt(i + 1));
        final int lo = hexValue(raw.charAt(i + 2));
        if (hi >= 0 && lo >= 0) {
          buffer.write((hi << 4) + lo);
          i += 3;
          continue;
        }
      }
      i += appendUtf8CodePoint(raw, i, buffer);
    }
    return buffer.toString(StandardCharsets.UTF_8);
  }

  private static int appendUtf8CodePoint(
      final String raw, final int offset, final ByteArrayOutputStream buffer) {
    final int codePoint = raw.codePointAt(offset);
    buffer.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
    return Character.charCount(codePoint);
  }

  private static int hexValue(final char ch) {
    if (ch >= '0' && ch <= '9') {
      return ch - '0';
    }
    if (ch >= 'a' && ch <= 'f') {
      return 10 + (ch - 'a');
    }
    if (ch >= 'A' && ch <= 'F') {
      return 10 + (ch - 'A');
    }
    return -1;
  }

  private static char toHexUpper(final int value) {
    return "0123456789ABCDEF".charAt(value & 0x0f);
  }
}
