package com.craftsmanbro.fulcraft.infrastructure.auth.aws.model;

import com.craftsmanbro.fulcraft.i18n.MessageSource;

/** Immutable AWS credential set used by infrastructure auth signers. */
public record AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
  private static final String REQUIRED_CREDENTIALS_MESSAGE_KEY = "infra.common.error.message";
  private static final String REQUIRED_CREDENTIALS_MESSAGE_FALLBACK =
      "AWS credentials are required";

  public AwsCredentials {
    // Session tokens are optional for long-lived credentials, but the key pair is always
    // required.
    if (isMissingRequiredKeyPair(accessKeyId, secretAccessKey)) {
      throw missingRequiredCredentialsException();
    }
  }

  private static boolean isMissingRequiredKeyPair(String accessKeyId, String secretAccessKey) {
    return isBlank(accessKeyId) || isBlank(secretAccessKey);
  }

  private static IllegalArgumentException missingRequiredCredentialsException() {
    return new IllegalArgumentException(
        MessageSource.getMessage(
            REQUIRED_CREDENTIALS_MESSAGE_KEY, REQUIRED_CREDENTIALS_MESSAGE_FALLBACK));
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
