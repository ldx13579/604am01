package com.example.configcenter.controller;

import com.example.configcenter.model.dto.EncryptionKeyDTO;
import com.example.configcenter.service.EncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/encryption")
public class EncryptionController {

    private final EncryptionService encryptionService;

    public EncryptionController(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    /**
     * Generates new RSA keypair + AES key for the given environment and namespace.
     * Returns the key info along with the RSA private key (one-time display).
     * ADMIN only.
     */
    @PostMapping("/keys/generate")
    public ResponseEntity<Map<String, Object>> generateKeys(@RequestBody GenerateKeysRequest request) {
        String environment = request.getEnvironment();
        String namespace = request.getNamespace() != null ? request.getNamespace() : "default";

        EncryptionKeyDTO dto = encryptionService.generateKeys(environment, namespace);

        // Extract private key from the combined rsaPublicKey field
        String combinedKey = dto.getRsaPublicKey();
        String publicKey;
        String privateKey;
        String delimiter = "\n---PRIVATE_KEY---\n";
        if (combinedKey != null && combinedKey.contains(delimiter)) {
            String[] parts = combinedKey.split(delimiter, 2);
            publicKey = parts[0];
            privateKey = parts[1];
        } else {
            publicKey = combinedKey;
            privateKey = null;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", dto.getId());
        response.put("environment", dto.getEnvironment());
        response.put("namespace", dto.getNamespace());
        response.put("rsaPublicKey", publicKey);
        response.put("rsaPrivateKey", privateKey);
        response.put("aesKeyEncrypted", dto.getAesKeyEncrypted());
        response.put("keyVersion", dto.getKeyVersion());
        response.put("createdAt", dto.getCreatedAt());
        response.put("warning", "Store the RSA private key securely. It will NOT be shown again.");

        return ResponseEntity.ok(response);
    }

    /**
     * Returns current key info (public key + encrypted AES key) for the given environment and namespace.
     * Authenticated access.
     */
    @GetMapping("/keys/{environment}/{namespace}")
    public ResponseEntity<EncryptionKeyDTO> getKeyInfo(
            @PathVariable String environment,
            @PathVariable String namespace) {
        EncryptionKeyDTO dto = encryptionService.getKeyInfo(environment, namespace);
        return ResponseEntity.ok(dto);
    }

    /**
     * Request body for key generation.
     */
    public static class GenerateKeysRequest {
        private String environment;
        private String namespace;

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }
}
