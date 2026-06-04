package com.example.configcenter.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "encryption_key", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"environment", "namespace", "key_version"})
})
public class EncryptionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "environment", nullable = false)
    private String environment;

    @Column(name = "namespace", nullable = false)
    private String namespace = "default";

    @Column(name = "aes_key_encrypted", columnDefinition = "TEXT", nullable = false)
    private String aesKeyEncrypted;

    @Column(name = "rsa_public_key", columnDefinition = "TEXT", nullable = false)
    private String rsaPublicKey;

    @Column(name = "key_version", nullable = false)
    private Integer keyVersion = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
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

    public String getAesKeyEncrypted() {
        return aesKeyEncrypted;
    }

    public void setAesKeyEncrypted(String aesKeyEncrypted) {
        this.aesKeyEncrypted = aesKeyEncrypted;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
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
