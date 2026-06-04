package com.example.configcenter.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "config_pull_record")
public class ConfigPullRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_item_id", nullable = false)
    private Long configItemId;

    @Column(name = "client_ip", nullable = false, length = 50)
    private String clientIp;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false, length = 100)
    private String namespace = "default";

    @Column(name = "pulled_at", nullable = false)
    private LocalDateTime pulledAt;

    @PrePersist
    public void prePersist() {
        if (pulledAt == null) pulledAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConfigItemId() { return configItemId; }
    public void setConfigItemId(Long configItemId) { this.configItemId = configItemId; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public LocalDateTime getPulledAt() { return pulledAt; }
    public void setPulledAt(LocalDateTime pulledAt) { this.pulledAt = pulledAt; }
}
