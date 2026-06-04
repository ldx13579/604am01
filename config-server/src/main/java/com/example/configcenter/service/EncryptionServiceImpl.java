package com.example.configcenter.service;

import com.example.configcenter.model.dto.EncryptionKeyDTO;
import com.example.configcenter.model.entity.EncryptionKey;
import com.example.configcenter.repository.EncryptionKeyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

@Service
public class EncryptionServiceImpl implements EncryptionService {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;
    private static final int RSA_KEY_SIZE = 2048;
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private final EncryptionKeyRepository encryptionKeyRepository;
    private final String masterKey;

    public EncryptionServiceImpl(EncryptionKeyRepository encryptionKeyRepository,
                                 @Value("${encryption.master-key}") String masterKey) {
        this.encryptionKeyRepository = encryptionKeyRepository;
        this.masterKey = masterKey;
    }

    @Override
    public String encrypt(String plaintext, String environment, String namespace) {
        try {
            byte[] aesKeyBytes = getDecryptedAesKey(environment, namespace);
            SecretKeySpec secretKey = new SecretKeySpec(aesKeyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] ciphertextBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext (IV + ciphertext + tag)
            byte[] combined = new byte[iv.length + ciphertextBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertextBytes, 0, combined, iv.length, ciphertextBytes.length);

            String encoded = Base64.getEncoder().encodeToString(combined);
            return ENC_PREFIX + encoded + ENC_SUFFIX;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    @Override
    public String decrypt(String ciphertext, String environment, String namespace) {
        try {
            if (!ciphertext.startsWith(ENC_PREFIX) || !ciphertext.endsWith(ENC_SUFFIX)) {
                throw new IllegalArgumentException("Ciphertext must be in format ENC(base64)");
            }

            String base64Content = ciphertext.substring(ENC_PREFIX.length(), ciphertext.length() - ENC_SUFFIX.length());
            byte[] combined = Base64.getDecoder().decode(base64Content);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            byte[] aesKeyBytes = getDecryptedAesKey(environment, namespace);
            SecretKeySpec secretKey = new SecretKeySpec(aesKeyBytes, "AES");

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(encrypted);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }

    @Override
    public EncryptionKeyDTO generateKeys(String environment, String namespace) {
        try {
            // Generate RSA-2048 keypair
            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(RSA_KEY_SIZE, new SecureRandom());
            KeyPair keyPair = rsaKeyGen.generateKeyPair();

            // Generate AES-256 key
            KeyGenerator aesKeyGen = KeyGenerator.getInstance("AES");
            aesKeyGen.init(AES_KEY_SIZE, new SecureRandom());
            SecretKey aesKey = aesKeyGen.generateKey();

            // Encrypt AES key with RSA public key
            Cipher rsaCipher = Cipher.getInstance(RSA_ALGORITHM);
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
            rsaCipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaepParams);
            byte[] encryptedAesKey = rsaCipher.doFinal(aesKey.getEncoded());
            String encryptedAesKeyBase64 = Base64.getEncoder().encodeToString(encryptedAesKey);

            // Encode public key as PEM
            String publicKeyPem = encodePublicKeyToPem(keyPair.getPublic());

            // Encode private key as PEM (for one-time return)
            String privateKeyPem = encodePrivateKeyToPem(keyPair.getPrivate());

            // Determine next key version
            int nextVersion = 1;
            List<EncryptionKey> existingKeys = encryptionKeyRepository.findByEnvironmentAndNamespace(environment, namespace);
            if (!existingKeys.isEmpty()) {
                nextVersion = existingKeys.stream()
                        .mapToInt(EncryptionKey::getKeyVersion)
                        .max()
                        .orElse(0) + 1;
            }

            // Also encrypt the AES key with the server master key for server-side usage
            String masterEncryptedAesKey = encryptWithMasterKey(aesKey.getEncoded());

            // Store in database
            EncryptionKey entity = new EncryptionKey();
            entity.setEnvironment(environment);
            entity.setNamespace(namespace);
            entity.setRsaPublicKey(publicKeyPem);
            entity.setAesKeyEncrypted(masterEncryptedAesKey);
            entity.setKeyVersion(nextVersion);
            encryptionKeyRepository.save(entity);

            // Build DTO with private key for one-time display
            EncryptionKeyDTO dto = new EncryptionKeyDTO();
            dto.setId(entity.getId());
            dto.setEnvironment(environment);
            dto.setNamespace(namespace);
            dto.setRsaPublicKey(publicKeyPem);
            dto.setAesKeyEncrypted(encryptedAesKeyBase64);
            dto.setKeyVersion(entity.getKeyVersion());
            dto.setCreatedAt(entity.getCreatedAt());

            // Store private key temporarily in a transient field via a wrapper approach
            // We return it as part of a special response in the controller
            // For now, store it in the rsaPublicKey field will not work; use a dedicated response map
            // Instead, we add a special marker: the DTO's rsaPublicKey will hold the private key for this call
            // Actually, the controller will handle this. We set the public key normally and
            // the controller returns the private key separately.
            // We'll use a convention: during generateKeys, we temporarily set aesKeyEncrypted
            // to the RSA-encrypted version (for client usage) rather than the master-key-encrypted version.
            // The privateKeyPem is returned by encoding it into a special field.
            // Best approach: return the private key PEM via the DTO by overloading rsaPublicKey
            // No -- let's keep the DTO clean and use a different approach in the controller.

            // Store private key PEM in a thread-local or return a map from the controller.
            // Simplest: store the private key in the DTO's rsaPublicKey temporarily,
            // and have the controller extract it. But this is messy.

            // Cleanest approach: return the private key as the rsaPublicKey field's companion.
            // Since the DTO doesn't have a privateKey field, we'll add the private key
            // info to the response at the controller level. For now, we encode it
            // by appending to the DTO - but since DTO has no field for it,
            // the controller will call this method, then separately get the private key.

            // Actually, the simplest and cleanest approach: the EncryptionKeyDTO is
            // returned from this service method. The controller can add the private key
            // to the response map. But the service needs to provide the private key somehow.
            // Let's store it in a transient way by reusing the aesKeyEncrypted field for
            // this one-time call with the RSA-public-encrypted AES key for the client,
            // and communicate the private key via an additional mechanism.

            // Final approach: We'll just put the private key PEM into the DTO's
            // rsaPublicKey field temporarily, and have a convention that during generation
            // this field contains: PUBLIC_KEY + "\n---PRIVATE---\n" + PRIVATE_KEY
            // The controller will split them.

            // Actually let's just be pragmatic: set aesKeyEncrypted to the RSA-encrypted
            // version (for client) and embed private key after a delimiter in rsaPublicKey.
            dto.setRsaPublicKey(publicKeyPem + "\n---PRIVATE_KEY---\n" + privateKeyPem);

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption keys", e);
        }
    }

    @Override
    public EncryptionKeyDTO getKeyInfo(String environment, String namespace) {
        EncryptionKey entity = encryptionKeyRepository
                .findFirstByEnvironmentAndNamespaceOrderByKeyVersionDesc(environment, namespace)
                .orElseThrow(() -> new RuntimeException(
                        "No encryption key found for environment=" + environment + ", namespace=" + namespace));

        EncryptionKeyDTO dto = new EncryptionKeyDTO();
        dto.setId(entity.getId());
        dto.setEnvironment(entity.getEnvironment());
        dto.setNamespace(entity.getNamespace());
        dto.setRsaPublicKey(entity.getRsaPublicKey());
        dto.setAesKeyEncrypted(entity.getAesKeyEncrypted());
        dto.setKeyVersion(entity.getKeyVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    /**
     * Decrypts the stored AES key using the server-side master key.
     */
    private byte[] getDecryptedAesKey(String environment, String namespace) {
        EncryptionKey entity = encryptionKeyRepository
                .findFirstByEnvironmentAndNamespaceOrderByKeyVersionDesc(environment, namespace)
                .orElseThrow(() -> new RuntimeException(
                        "No encryption key found for environment=" + environment + ", namespace=" + namespace));

        return decryptWithMasterKey(entity.getAesKeyEncrypted());
    }

    /**
     * Encrypts data with the server-side master key using AES/GCM.
     */
    private String encryptWithMasterKey(byte[] data) {
        try {
            byte[] masterKeyBytes = getMasterKeyBytes();
            SecretKeySpec masterSecretKey = new SecretKeySpec(masterKeyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterSecretKey, gcmSpec);

            byte[] encrypted = cipher.doFinal(data);

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt with master key", e);
        }
    }

    /**
     * Decrypts data with the server-side master key using AES/GCM.
     */
    private byte[] decryptWithMasterKey(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] masterKeyBytes = getMasterKeyBytes();
            SecretKeySpec masterSecretKey = new SecretKeySpec(masterKeyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterSecretKey, gcmSpec);

            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt with master key", e);
        }
    }

    /**
     * Derives a 256-bit key from the configured master key string.
     */
    private byte[] getMasterKeyBytes() {
        byte[] keyBytes = masterKey.getBytes(StandardCharsets.UTF_8);
        // Ensure 32 bytes for AES-256; pad or truncate
        byte[] result = new byte[32];
        System.arraycopy(keyBytes, 0, result, 0, Math.min(keyBytes.length, 32));
        return result;
    }

    private String encodePublicKeyToPem(PublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    private String encodePrivateKeyToPem(PrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
