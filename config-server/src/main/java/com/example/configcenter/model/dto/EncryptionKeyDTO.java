package com.example.configcenter.model.dto;

import java.time.LocalDateTime;

public class EncryptionKeyDTO {

    private Long id;
    private String environment;
    private String namespace;
    private String rsaPublicKey;
    private String aesKeyEncrypted;
    private Integer keyVersion;
    private LocalDateTime createdAt;

    public EncryptionKeyDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getAesKeyEncrypted() {
        return aesKeyEncrypted;
    }

    public void setAesKeyEncrypted(String aesKeyEncrypted) {
        this.aesKeyEncrypted = aesKeyEncrypted;
    }

    public Integer getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(Integer keyVersion) {
        this.keyVersion = keyVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
