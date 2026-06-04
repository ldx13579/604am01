package com.example.configcenter.service;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.ConfigVersion;
import java.util.List;

public interface ConfigService {

    List<ConfigItemDTO> listConfigs(String environment, String namespace);

    ConfigItemDTO getConfig(Long id);

    ConfigItemDTO createConfig(ConfigCreateRequest request);

    ConfigItemDTO updateConfig(Long id, ConfigUpdateRequest request);

    void deleteConfig(Long id);

    List<ConfigVersion> getVersionHistory(Long id);

    ConfigItemDTO rollback(Long id, Long targetVersion);

    Long getCurrentVersion(String environment, String namespace);
}
