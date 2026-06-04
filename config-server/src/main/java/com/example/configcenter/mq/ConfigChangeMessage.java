package com.example.configcenter.mq;

import java.io.Serializable;

public class ConfigChangeMessage implements Serializable {

    private String environment;
    private String namespace;
    private Long version;
    private String operation;

    public ConfigChangeMessage() {}

    public ConfigChangeMessage(String environment, String namespace, Long version, String operation) {
        this.environment = environment;
        this.namespace = namespace;
        this.version = version;
        this.operation = operation;
    }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
}
