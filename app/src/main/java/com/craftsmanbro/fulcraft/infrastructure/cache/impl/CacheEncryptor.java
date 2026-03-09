package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import com.craftsmanbro.fulcraft.infrastructure.logging.impl.Logger;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles encryption and decryption of cached data using AES/GCM.
 *
 * <p>
 * This class is package-private and used only within the cache package.
 */
final class CacheEncryptor {

  private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";

  private static final int AES_KEY_LENGTH_BYTES = 16;

  private static final int GCM_IV_LENGTH = 12;

  private static final int GCM_TAG_LENGTH = 128;

  private final boolean enabled;

  private final String encryptionKey;

  private final SecureRandom secureRandom;

  /**
   * Creates a disabled encryptor that passes through data without encryption.
   *
   * @return a no-op encryptor
   */
  public static CacheEncryptor disabled() {
    return new CacheEncryptor(false, null);
  }

  /**
   * Creates an encryptor with the specified encryption key.
   *
   * @param encryptionKey the encryption key (will be hashed to derive AES key)
   * @return an enabled encryptor
   */
  public static CacheEncryptor withKey(final String encryptionKey) {
    Objects.requireNonNull(
        encryptionKey,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "encryptionKey must not be null"));
    return new CacheEncryptor(true, encryptionKey);
  }

  private CacheEncryptor(final boolean enabled, final String encryptionKey) {
    this.enabled = enabled;
    this.encryptionKey = encryptionKey;
    this.secureRandom = new SecureRandom();
  }

  /**
   * Returns whether encryption is enabled.
   *
   * @return true if encryption is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Encrypts the given plain text.
   *
   * @param plainText the text to encrypt
   * @return the encrypted text (Base64-encoded), or the original text if
   *         encryption is disabled
   */
  public String encrypt(final String plainText) {
    if (!enabled || encryptionKey == null) {
      return plainText;
    }
    try {
      final SecretKeySpec keySpec = deriveKeySpec();
      final byte[] initializationVector = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(initializationVector);
      final Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
      final GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
      final byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      // Prepend IV to ciphertext
      final byte[] combinedCipherBytes = new byte[initializationVector.length + encryptedBytes.length];
      System.arraycopy(
          initializationVector, 0, combinedCipherBytes, 0, initializationVector.length);
      System.arraycopy(
          encryptedBytes,
          0,
          combinedCipherBytes,
          initializationVector.length,
          encryptedBytes.length);
      return Base64.getEncoder().encodeToString(combinedCipherBytes);
    } catch (GeneralSecurityException e) {
      Logger.warn(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Encryption failed: " + e.getMessage()));
      // Fallback to plain text on failure
      return plainText;
    }
  }

  /**
   * Decrypts the given cipher text.
   *
   * @param cipherText the text to decrypt (Base64-encoded)
   * @return the decrypted text, or null if decryption fails
   */
  public String decrypt(final String cipherText) {
    if (!enabled || encryptionKey == null) {
      return cipherText;
    }
    try {
      final SecretKeySpec keySpec = deriveKeySpec();
      final byte[] combinedCipherBytes = Base64.getDecoder().decode(cipherText);
      if (combinedCipherBytes.length <= GCM_IV_LENGTH) {
        Logger.debug(
            com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
                "infra.common.log.message", "Decryption failed: cipher text too short"));
        return null;
      }
      // Extract IV from the beginning
      final byte[] initializationVector = new byte[GCM_IV_LENGTH];
      final byte[] encryptedBytes = new byte[combinedCipherBytes.length - GCM_IV_LENGTH];
      System.arraycopy(
          combinedCipherBytes, 0, initializationVector, 0, initializationVector.length);
      System.arraycopy(
          combinedCipherBytes,
          initializationVector.length,
          encryptedBytes,
          0,
          encryptedBytes.length);
      final Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
      final GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, initializationVector);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
      return new String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      Logger.debug(
          com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
              "infra.common.log.message", "Decryption failed: " + e.getMessage()));
      return null;
    }
  }

  private SecretKeySpec deriveKeySpec() {
    final String hashedKeyText = computeHash(encryptionKey);
    return new SecretKeySpec(
        hashedKeyText.substring(0, AES_KEY_LENGTH_BYTES).getBytes(StandardCharsets.UTF_8), "AES");
  }

  private static String computeHash(final String content) {
    final String contentToHash = Objects.requireNonNull(
        content,
        com.craftsmanbro.fulcraft.i18n.MessageSource.getMessage(
            "infra.common.error.argument_null", "content must not be null"));
    return Hashing.sha256().hashString(contentToHash, Objects.requireNonNull(StandardCharsets.UTF_8)).toString();
  }
}
