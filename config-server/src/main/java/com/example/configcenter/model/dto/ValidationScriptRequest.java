package com.example.configcenter.model.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidationScriptRequest {

    @NotBlank(message = "environment is required")
    private String environment;

    private String namespace;

    @NotBlank(message = "scriptName is required")
    private String scriptName;

    @NotBlank(message = "scriptContent is required")
    private String scriptContent;

    private Boolean enabled;

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getScriptName() { return scriptName; }
    public void setScriptName(String scriptName) { this.scriptName = scriptName; }

    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
