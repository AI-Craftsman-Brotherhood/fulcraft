package com.craftsmanbro.fulcraft.infrastructure.cache.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CacheEncryptorTest {

  @Test
  void testDisabledEncryptor() {
    CacheEncryptor encryptor = CacheEncryptor.disabled();

    assertFalse(encryptor.isEnabled());
  }

  @Test
  void testDisabledEncryptorPassesThrough() {
    CacheEncryptor encryptor = CacheEncryptor.disabled();
    String plainText = "Hello, World!";

    String encrypted = encryptor.encrypt(plainText);
    String decrypted = encryptor.decrypt(encrypted);

    assertEquals(plainText, encrypted);
    assertEquals(plainText, decrypted);
  }

  @Test
  void testWithKeyCreatesEnabledEncryptor() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");

    assertTrue(encryptor.isEnabled());
  }

  @Test
  void testWithKeyNullThrowsException() {
    assertThrows(NullPointerException.class, () -> CacheEncryptor.withKey(null));
  }

  @Test
  void testEncryptProducesDifferentOutput() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "Hello, World!";

    String encrypted = encryptor.encrypt(plainText);

    assertNotEquals(plainText, encrypted);
    assertNotNull(encrypted);
  }

  @Test
  void testEncryptDecryptRoundTrip() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "This is a test message for encryption.";

    String encrypted = encryptor.encrypt(plainText);
    String decrypted = encryptor.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }

  @Test
  void testEncryptDecryptWithSpecialCharacters() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "日本語テスト with émojis 🎉 and symbols <>&\"'";

    String encrypted = encryptor.encrypt(plainText);
    String decrypted = encryptor.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }

  @Test
  void testEncryptDecryptWithLongContent() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "Long content ".repeat(1000);

    String encrypted = encryptor.encrypt(plainText);
    String decrypted = encryptor.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }

  @Test
  void testDecryptWithWrongKeyReturnsNull() {
    CacheEncryptor encryptor1 = CacheEncryptor.withKey("secret-key-1");
    CacheEncryptor encryptor2 = CacheEncryptor.withKey("secret-key-2");
    String plainText = "Hello, World!";

    String encrypted = encryptor1.encrypt(plainText);
    String decrypted = encryptor2.decrypt(encrypted);

    // Decryption with wrong key should return null
    assertNull(decrypted);
  }

  @Test
  void testDecryptInvalidCipherTextReturnsNull() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");

    String decrypted = encryptor.decrypt("not-a-valid-base64-ciphertext!!!");

    assertNull(decrypted);
  }

  @Test
  void testDecryptShortCipherTextReturnsNull() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String shortCipherText = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});

    String decrypted = encryptor.decrypt(shortCipherText);

    assertNull(decrypted);
  }

  @Test
  void testDisabledDecryptPassesThroughAnyText() {
    CacheEncryptor encryptor = CacheEncryptor.disabled();
    String rawText = "not-base64-and-not-encrypted";

    String decrypted = encryptor.decrypt(rawText);

    assertEquals(rawText, decrypted);
  }

  @Test
  void testEachEncryptionProducesUniqueOutput() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "Same plain text";

    String encrypted1 = encryptor.encrypt(plainText);
    String encrypted2 = encryptor.encrypt(plainText);

    // Due to random IV, each encryption should produce different ciphertext
    assertNotEquals(encrypted1, encrypted2);

    // But both should decrypt to the same plain text
    assertEquals(plainText, encryptor.decrypt(encrypted1));
    assertEquals(plainText, encryptor.decrypt(encrypted2));
  }

  @Test
  void testEmptyStringEncryption() {
    CacheEncryptor encryptor = CacheEncryptor.withKey("secret-key");
    String plainText = "";

    String encrypted = encryptor.encrypt(plainText);
    String decrypted = encryptor.decrypt(encrypted);

    assertEquals(plainText, decrypted);
  }
}
