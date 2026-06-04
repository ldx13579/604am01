package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_item", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"config_key", "environment", "namespace"})
})
public class ConfigItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false, length = 100)
    private String namespace = "default";

    @Column(length = 500)
    private String description = "";

    @Column(nullable = false)
    private Boolean encrypted = false;

    @Column(nullable = false)
    private Long version = 1L;

    @Column(name = "last_pulled_at")
    private LocalDateTime lastPulledAt;

    @Column(nullable = false)
    private Boolean zombie = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getEncrypted() { return encrypted; }
    public void setEncrypted(Boolean encrypted) { this.encrypted = encrypted; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getLastPulledAt() { return lastPulledAt; }
    public void setLastPulledAt(LocalDateTime lastPulledAt) { this.lastPulledAt = lastPulledAt; }

    public Boolean getZombie() { return zombie; }
    public void setZombie(Boolean zombie) { this.zombie = zombie; }
}
