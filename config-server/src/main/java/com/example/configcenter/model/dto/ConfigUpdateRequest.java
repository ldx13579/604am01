package com.example.configcenter.model.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfigUpdateRequest {

    @NotBlank
    private String configValue;

    private String description;

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
