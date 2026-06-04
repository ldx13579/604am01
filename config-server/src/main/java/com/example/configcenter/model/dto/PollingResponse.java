package com.example.configcenter.model.dto;

import java.util.List;

public class PollingResponse {

    private Long version;
    private boolean hasChange;
    private List<ConfigItemDTO> configs;

    public PollingResponse() {}

    public PollingResponse(Long version, boolean hasChange, List<ConfigItemDTO> configs) {
        this.version = version;
        this.hasChange = hasChange;
        this.configs = configs;
    }

    public static PollingResponse noChange(Long version) {
        return new PollingResponse(version, false, List.of());
    }

    public static PollingResponse changed(Long version, List<ConfigItemDTO> configs) {
        return new PollingResponse(version, true, configs);
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public boolean isHasChange() { return hasChange; }
    public void setHasChange(boolean hasChange) { this.hasChange = hasChange; }

    public List<ConfigItemDTO> getConfigs() { return configs; }
    public void setConfigs(List<ConfigItemDTO> configs) { this.configs = configs; }
}
