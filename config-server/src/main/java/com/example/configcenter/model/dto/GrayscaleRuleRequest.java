package com.example.configcenter.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class GrayscaleRuleRequest {

    @NotBlank
    private String environment;

    private String namespace = "default";

    @NotBlank
    private String ruleName;

    @NotNull
    private Long targetVersion;

    @NotBlank
    private String ipList;

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public Long getTargetVersion() { return targetVersion; }
    public void setTargetVersion(Long targetVersion) { this.targetVersion = targetVersion; }

    public String getIpList() { return ipList; }
    public void setIpList(String ipList) { this.ipList = ipList; }
}
