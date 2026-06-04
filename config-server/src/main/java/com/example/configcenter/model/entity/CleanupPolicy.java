package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cleanup_policy")
public class CleanupPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 200)
    private String policyName;

    @Column(length = 20)
    private String environment;

    @Column(length = 100)
    private String namespace;

    @Column(name = "key_pattern", length = 200)
    private String keyPattern;

    @Column(name = "min_age_days")
    private Integer minAgeDays;

    @Column(name = "max_value_size_bytes")
    private Integer maxValueSizeBytes;

    @Column(name = "min_value_size_bytes")
    private Integer minValueSizeBytes;

    @Column(name = "require_zombie", nullable = false)
    private Boolean requireZombie = true;

    @Column(name = "condition_logic", nullable = false, length = 10)
    private String conditionLogic = "AND";

    @Column(name = "cleanup_action", nullable = false, length = 20)
    private String cleanupAction = "ARCHIVED";

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

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

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getKeyPattern() { return keyPattern; }
    public void setKeyPattern(String keyPattern) { this.keyPattern = keyPattern; }

    public Integer getMinAgeDays() { return minAgeDays; }
    public void setMinAgeDays(Integer minAgeDays) { this.minAgeDays = minAgeDays; }

    public Integer getMaxValueSizeBytes() { return maxValueSizeBytes; }
    public void setMaxValueSizeBytes(Integer maxValueSizeBytes) { this.maxValueSizeBytes = maxValueSizeBytes; }

    public Integer getMinValueSizeBytes() { return minValueSizeBytes; }
    public void setMinValueSizeBytes(Integer minValueSizeBytes) { this.minValueSizeBytes = minValueSizeBytes; }

    public Boolean getRequireZombie() { return requireZombie; }
    public void setRequireZombie(Boolean requireZombie) { this.requireZombie = requireZombie; }

    public String getConditionLogic() { return conditionLogic; }
    public void setConditionLogic(String conditionLogic) { this.conditionLogic = conditionLogic; }

    public String getCleanupAction() { return cleanupAction; }
    public void setCleanupAction(String cleanupAction) { this.cleanupAction = cleanupAction; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
