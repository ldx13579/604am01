package com.example.configcenter.model.dto;

import jakarta.validation.constraints.NotBlank;

public class ValidationTestRequest {

    @NotBlank(message = "scriptContent is required")
    private String scriptContent;

    @NotBlank(message = "testConfigKey is required")
    private String testConfigKey;

    @NotBlank(message = "testConfigValue is required")
    private String testConfigValue;

    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }

    public String getTestConfigKey() { return testConfigKey; }
    public void setTestConfigKey(String testConfigKey) { this.testConfigKey = testConfigKey; }

    public String getTestConfigValue() { return testConfigValue; }
    public void setTestConfigValue(String testConfigValue) { this.testConfigValue = testConfigValue; }
}
