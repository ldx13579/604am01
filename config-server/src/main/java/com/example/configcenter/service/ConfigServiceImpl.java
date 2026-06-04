package com.example.configcenter.service;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.*;
import com.example.configcenter.repository.*;
import com.example.configcenter.exception.ConfigNotFoundException;
import com.example.configcenter.exception.ConfigAlreadyExistsException;
import com.example.configcenter.exception.VersionNotFoundException;
import com.example.configcenter.mq.ConfigChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfigServiceImpl implements ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigServiceImpl.class);

    private final ConfigItemRepository configItemRepo;
    private final ConfigVersionRepository configVersionRepo;
    private final VersionCounterRepository versionCounterRepo;
    private final NotificationService notificationService;

    @Autowired(required = false)
    private ConfigChangePublisher changePublisher;

    public ConfigServiceImpl(ConfigItemRepository configItemRepo,
                             ConfigVersionRepository configVersionRepo,
                             VersionCounterRepository versionCounterRepo,
                             NotificationService notificationService) {
        this.configItemRepo = configItemRepo;
        this.configVersionRepo = configVersionRepo;
        this.versionCounterRepo = versionCounterRepo;
        this.notificationService = notificationService;
    }

    @Override
    public List<ConfigItemDTO> listConfigs(String environment, String namespace) {
        return configItemRepo.findByEnvironmentAndNamespace(environment, namespace)
                .stream().map(this::toDTO).toList();
    }

    @Override
    public ConfigItemDTO getConfig(Long id) {
        ConfigItem item = configItemRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found: " + id));
        return toDTO(item);
    }

    @Override
    @Transactional
    public ConfigItemDTO createConfig(ConfigCreateRequest request) {
        if (configItemRepo.existsByConfigKeyAndEnvironmentAndNamespace(
                request.getConfigKey(), request.getEnvironment(), request.getNamespace())) {
            throw new ConfigAlreadyExistsException("Config key already exists in this environment/namespace");
        }

        Long newVersion = incrementVersion(request.getEnvironment(), request.getNamespace());

        ConfigItem item = new ConfigItem();
        item.setConfigKey(request.getConfigKey());
        item.setConfigValue(request.getConfigValue());
        item.setEnvironment(request.getEnvironment());
        item.setNamespace(request.getNamespace());
        item.setDescription(request.getDescription());
        item.setVersion(newVersion);
        item = configItemRepo.save(item);

        saveVersionHistory(item, "CREATE");
        publishChange(request.getEnvironment(), request.getNamespace(), newVersion, "CREATE");

        return toDTO(item);
    }

    @Override
    @Transactional
    public ConfigItemDTO updateConfig(Long id, ConfigUpdateRequest request) {
        ConfigItem item = configItemRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found: " + id));

        if (!request.getExpectedVersion().equals(item.getVersion())) {
            throw new com.example.configcenter.exception.VersionConflictException(
                    "Version conflict: expected " + request.getExpectedVersion() + ", current " + item.getVersion());
        }

        Long newVersion = incrementVersion(item.getEnvironment(), item.getNamespace());

        item.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        item.setVersion(newVersion);
        item = configItemRepo.save(item);

        saveVersionHistory(item, "UPDATE");
        publishChange(item.getEnvironment(), item.getNamespace(), newVersion, "UPDATE");

        return toDTO(item);
    }

    @Override
    @Transactional
    public void deleteConfig(Long id) {
        ConfigItem item = configItemRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found: " + id));

        Long newVersion = incrementVersion(item.getEnvironment(), item.getNamespace());
        item.setVersion(newVersion);
        saveVersionHistory(item, "DELETE");

        configItemRepo.delete(item);
        publishChange(item.getEnvironment(), item.getNamespace(), newVersion, "DELETE");
    }

    @Override
    public List<ConfigVersion> getVersionHistory(Long id) {
        configItemRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found: " + id));
        return configVersionRepo.findByConfigItemIdOrderByVersionDesc(id);
    }

    @Override
    @Transactional
    public ConfigItemDTO rollback(Long id, Long targetVersion) {
        ConfigItem item = configItemRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Config not found: " + id));

        ConfigVersion target = configVersionRepo.findByConfigItemIdAndVersion(id, targetVersion)
                .orElseThrow(() -> new VersionNotFoundException("Version not found: " + targetVersion));

        Long newVersion = incrementVersion(item.getEnvironment(), item.getNamespace());

        item.setConfigKey(target.getConfigKey());
        item.setConfigValue(target.getConfigValue());
        item.setDescription(target.getDescription());
        item.setEnvironment(target.getEnvironment());
        item.setNamespace(target.getNamespace());
        item.setVersion(newVersion);
        item = configItemRepo.save(item);

        saveVersionHistory(item, "ROLLBACK");
        publishChange(item.getEnvironment(), item.getNamespace(), newVersion, "ROLLBACK");

        return toDTO(item);
    }

    @Override
    public Long getCurrentVersion(String environment, String namespace) {
        return versionCounterRepo.findByEnvironmentAndNamespace(environment, namespace)
                .map(VersionCounter::getCurrentVersion)
                .orElse(0L);
    }

    private Long incrementVersion(String environment, String namespace) {
        VersionCounter counter = versionCounterRepo.findByEnvironmentAndNamespace(environment, namespace)
                .orElseGet(() -> {
                    VersionCounter vc = new VersionCounter();
                    vc.setEnvironment(environment);
                    vc.setNamespace(namespace);
                    vc.setCurrentVersion(0L);
                    return vc;
                });
        counter.setCurrentVersion(counter.getCurrentVersion() + 1);
        versionCounterRepo.save(counter);
        return counter.getCurrentVersion();
    }

    private void saveVersionHistory(ConfigItem item, String operation) {
        ConfigVersion version = new ConfigVersion();
        version.setConfigItemId(item.getId());
        version.setConfigKey(item.getConfigKey());
        version.setConfigValue(item.getConfigValue());
        version.setEnvironment(item.getEnvironment());
        version.setNamespace(item.getNamespace());
        version.setVersion(item.getVersion());
        version.setOperation(operation);
        version.setDescription(item.getDescription());
        configVersionRepo.save(version);
    }

    private void publishChange(String environment, String namespace, Long version, String operation) {
        if (changePublisher != null) {
            try {
                changePublisher.publishChange(environment, namespace, version, operation);
            } catch (Exception e) {
                log.warn("RabbitMQ publish failed, falling back to local notification. Reason: {}", e.getMessage());
                notificationService.notifyChange(environment, namespace);
            }
        } else {
            log.warn("RabbitMQ is not configured. Config change events are only broadcast locally.");
            notificationService.notifyChange(environment, namespace);
        }
    }

    private ConfigItemDTO toDTO(ConfigItem item) {
        ConfigItemDTO dto = new ConfigItemDTO();
        dto.setId(item.getId());
        dto.setConfigKey(item.getConfigKey());
        dto.setConfigValue(item.getConfigValue());
        dto.setEnvironment(item.getEnvironment());
        dto.setNamespace(item.getNamespace());
        dto.setDescription(item.getDescription());
        dto.setVersion(item.getVersion());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        return dto;
    }
}
