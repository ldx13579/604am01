package com.example.configcenter.model.dto;

import java.time.LocalDateTime;

public class ClientInstanceDTO {

    private Long id;
    private String clientIp;
    private String environment;
    private String namespace;
    private Long currentVersion;
    private String status;
    private LocalDateTime lastHeartbeat;
    private boolean inGrayscale;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public Long getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Long currentVersion) { this.currentVersion = currentVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
    public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }

    public boolean isInGrayscale() { return inGrayscale; }
    public void setInGrayscale(boolean inGrayscale) { this.inGrayscale = inGrayscale; }
}
