package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "zombie_cleanup_log")
public class ZombieCleanupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_item_id", nullable = false)
    private Long configItemId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false, length = 100)
    private String namespace = "default";

    @Column(name = "last_pulled_at")
    private LocalDateTime lastPulledAt;

    @Column(name = "cleanup_action", nullable = false, length = 20)
    private String cleanupAction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConfigItemId() { return configItemId; }
    public void setConfigItemId(Long configItemId) { this.configItemId = configItemId; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public LocalDateTime getLastPulledAt() { return lastPulledAt; }
    public void setLastPulledAt(LocalDateTime lastPulledAt) { this.lastPulledAt = lastPulledAt; }

    public String getCleanupAction() { return cleanupAction; }
    public void setCleanupAction(String cleanupAction) { this.cleanupAction = cleanupAction; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
