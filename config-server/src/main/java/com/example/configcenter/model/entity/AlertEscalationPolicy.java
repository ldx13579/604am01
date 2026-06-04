package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_escalation_policy")
public class AlertEscalationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_name", nullable = false, length = 200)
    private String policyName;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "role_group", nullable = false, length = 50)
    private String roleGroup;

    @Column(name = "escalation_level", nullable = false)
    private Integer escalationLevel;

    @Column(name = "escalation_delay_minutes", nullable = false)
    private Integer escalationDelayMinutes = 0;

    @Column(name = "notify_channels", nullable = false, length = 500)
    private String notifyChannels;

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

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRoleGroup() { return roleGroup; }
    public void setRoleGroup(String roleGroup) { this.roleGroup = roleGroup; }

    public Integer getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(Integer escalationLevel) { this.escalationLevel = escalationLevel; }

    public Integer getEscalationDelayMinutes() { return escalationDelayMinutes; }
    public void setEscalationDelayMinutes(Integer escalationDelayMinutes) { this.escalationDelayMinutes = escalationDelayMinutes; }

    public String getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(String notifyChannels) { this.notifyChannels = notifyChannels; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
