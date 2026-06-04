package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_change_test_log")
public class ConfigChangeTestLog {

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

    @Column(name = "new_value", nullable = false, columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "test_result", nullable = false, length = 20)
    private String testResult;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "rolled_back", nullable = false)
    private Boolean rolledBack = false;

    @Column(name = "rollback_version")
    private Long rollbackVersion;

    @Column(name = "duration_ms")
    private Long durationMs;

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

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getTestResult() { return testResult; }
    public void setTestResult(String testResult) { this.testResult = testResult; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Boolean getRolledBack() { return rolledBack; }
    public void setRolledBack(Boolean rolledBack) { this.rolledBack = rolledBack; }

    public Long getRollbackVersion() { return rollbackVersion; }
    public void setRollbackVersion(Long rollbackVersion) { this.rollbackVersion = rollbackVersion; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
