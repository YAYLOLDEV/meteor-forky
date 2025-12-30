package meteordevelopment.meteorclient.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Simple AES-256-CBC encryption utility.
 * Uses a 64-byte key, derives a 32-byte AES key from it via SHA-256.
 */
public class CryptUtil {
  private static final int IV_LENGTH = 16;
  private static final int KEY_SIZE = 64;

  public static byte[] encrypt(byte[] data, byte[] key) {
    validateKey(key);

    try {
      byte[] aesKey = deriveAesKey(key);
      byte[] iv = new byte[IV_LENGTH];
      new SecureRandom().nextBytes(iv);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
      IvParameterSpec ivSpec = new IvParameterSpec(iv);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

      byte[] encrypted = cipher.doFinal(data);

      // Prepend IV to ciphertext
      byte[] result = new byte[IV_LENGTH + encrypted.length];
      System.arraycopy(iv, 0, result, 0, IV_LENGTH);
      System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);

      return result;
    } catch (Exception e) {
      throw new RuntimeException("Encryption failed", e);
    }
  }

  public static byte[] decrypt(byte[] data, byte[] key) {
    validateKey(key);

    try {
      byte[] aesKey = deriveAesKey(key);

      byte[] iv = Arrays.copyOfRange(data, 0, IV_LENGTH);
      byte[] encrypted = Arrays.copyOfRange(data, IV_LENGTH, data.length);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
      IvParameterSpec ivSpec = new IvParameterSpec(iv);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

      return cipher.doFinal(encrypted);
    } catch (Exception e) {
      throw new RuntimeException("Decryption failed", e);
    }
  }

  private static void validateKey(byte[] key) {
    if (key == null || key.length != KEY_SIZE) {
      throw new IllegalArgumentException("Key must be exactly " + KEY_SIZE + " bytes");
    }
  }

  private static byte[] deriveAesKey(byte[] key) throws Exception {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    return md.digest(key);
  }
}
