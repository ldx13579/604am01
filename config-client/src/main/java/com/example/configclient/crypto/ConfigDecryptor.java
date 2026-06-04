package com.example.configclient.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class ConfigDecryptor {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final String rsaPrivateKeyPath;

    public ConfigDecryptor(String rsaPrivateKeyPath) {
        this.rsaPrivateKeyPath = rsaPrivateKeyPath;
    }

    /**
     * Decrypts an encrypted config value.
     *
     * @param encryptedValue the encrypted value in "ENC(base64)" format
     * @param encryptedAesKey the RSA-encrypted AES key (base64 encoded)
     * @return the decrypted plaintext
     */
    public String decrypt(String encryptedValue, String encryptedAesKey) {
        try {
            // Load RSA private key from PEM file
            PrivateKey privateKey = loadPrivateKey();

            // Decrypt the AES key using RSA private key
            byte[] aesKeyBytes = decryptAesKey(encryptedAesKey, privateKey);

            // Strip ENC( prefix and ) suffix
            if (!isEncrypted(encryptedValue)) {
                throw new IllegalArgumentException("Value is not encrypted. Expected format: ENC(base64)");
            }
            String base64Content = encryptedValue.substring(ENC_PREFIX.length(),
                    encryptedValue.length() - ENC_SUFFIX.length());

            // Base64-decode the ciphertext
            byte[] combined = Base64.getDecoder().decode(base64Content);

            // Extract 12-byte IV from beginning
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Decrypt with AES/GCM/NoPadding
            SecretKeySpec secretKey = new SecretKeySpec(aesKeyBytes, "AES");
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt config value", e);
        }
    }

    /**
     * Checks if a value is encrypted (starts with "ENC(").
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
    }

    /**
     * Loads the RSA private key from the PEM file.
     */
    private PrivateKey loadPrivateKey() throws Exception {
        String pemContent = readPemFile();

        // Remove PEM headers and whitespace
        String base64Key = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Reads the PEM file content from the configured path.
     */
    private String readPemFile() throws IOException {
        return Files.readString(Path.of(rsaPrivateKeyPath), StandardCharsets.UTF_8);
    }

    /**
     * Decrypts the AES key using the RSA private key.
     */
    private byte[] decryptAesKey(String encryptedAesKeyBase64, PrivateKey privateKey) throws Exception {
        byte[] encryptedAesKey = Base64.getDecoder().decode(encryptedAesKeyBase64);

        Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);

        return rsaCipher.doFinal(encryptedAesKey);
    }
}
