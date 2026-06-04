package com.example.configcenter.service;

import com.example.configcenter.model.dto.EncryptionKeyDTO;

public interface EncryptionService {

    /**
     * Encrypts plaintext using AES-GCM for the given environment and namespace.
     * Returns the ciphertext in the format "ENC(base64)".
     */
    String encrypt(String plaintext, String environment, String namespace);

    /**
     * Decrypts ciphertext (in "ENC(base64)" format) using AES-GCM
     * for the given environment and namespace.
     */
    String decrypt(String ciphertext, String environment, String namespace);

    /**
     * Generates a new RSA-2048 keypair and AES-256 key for the given environment and namespace.
     * The AES key is encrypted with the RSA public key and stored in the database.
     * The RSA private key is returned (one-time) and NOT stored.
     */
    EncryptionKeyDTO generateKeys(String environment, String namespace);

    /**
     * Returns the current key info (public key + encrypted AES key) for the given environment and namespace.
     */
    EncryptionKeyDTO getKeyInfo(String environment, String namespace);
}
