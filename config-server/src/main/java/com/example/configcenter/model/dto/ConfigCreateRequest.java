package com.example.configcenter.model.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigCreateRequest {

    @NotBlank
    private String configKey;

    @NotBlank
    private String configValue;

    @NotBlank
    private String environment;

    private String namespace = "default";

    private String description = "";

    private Boolean encrypted = false;

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
}
