package com.example.configcenter.service;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.*;
import com.example.configcenter.repository.*;
import com.example.configcenter.exception.ConfigNotFoundException;
import com.example.configcenter.exception.ConfigAlreadyExistsException;
import com.example.configcenter.exception.VersionNotFoundException;
import com.example.configcenter.exception.ValidationFailedException;
import com.example.configcenter.metrics.MetricsService;
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

    @Autowired(required = false)
    private EncryptionService encryptionService;

    @Autowired(required = false)
    private ValidationService validationService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private ConfigChangeTestService configChangeTestService;

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

        if (validationService != null) {
            ValidationResult result = validationService.validate(
                    request.getConfigKey(), request.getConfigValue(),
                    request.getEnvironment(), request.getNamespace());
            if (!result.isValid()) {
                throw new ValidationFailedException(result.getMessage());
            }
        }

        Long newVersion = incrementVersion(request.getEnvironment(), request.getNamespace());

        String valueToStore = request.getConfigValue();
        boolean encrypted = Boolean.TRUE.equals(request.getEncrypted());
        if (encrypted && encryptionService != null) {
            valueToStore = encryptionService.encrypt(valueToStore, request.getEnvironment(), request.getNamespace());
        }

        ConfigItem item = new ConfigItem();
        item.setConfigKey(request.getConfigKey());
        item.setConfigValue(valueToStore);
        item.setEnvironment(request.getEnvironment());
        item.setNamespace(request.getNamespace());
        item.setDescription(request.getDescription());
        item.setEncrypted(encrypted);
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

        if (validationService != null) {
            ValidationResult result = validationService.validate(
                    item.getConfigKey(), request.getConfigValue(),
                    item.getEnvironment(), item.getNamespace());
            if (!result.isValid()) {
                throw new ValidationFailedException(result.getMessage());
            }
        }

        Long newVersion = incrementVersion(item.getEnvironment(), item.getNamespace());

        String valueToStore = request.getConfigValue();
        if (item.getEncrypted() && encryptionService != null) {
            valueToStore = encryptionService.encrypt(valueToStore, item.getEnvironment(), item.getNamespace());
        }

        item.setConfigValue(valueToStore);
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        item.setVersion(newVersion);
        item = configItemRepo.save(item);

        saveVersionHistory(item, "UPDATE");

        if (configChangeTestService != null) {
            boolean passed = configChangeTestService.testConfigChange(
                    item.getId(), item.getConfigKey(), request.getConfigValue(),
                    item.getEnvironment(), item.getNamespace(), request.getExpectedVersion());
            if (!passed) {
                ConfigItemDTO rolledBack = rollback(item.getId(), request.getExpectedVersion());
                return rolledBack;
            }
        }

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
        if (metricsService != null) {
            metricsService.recordConfigChange(environment, operation);
        }
        if (changePublisher != null) {
            try {
                changePublisher.publishChange(environment, namespace, version, operation);
            } catch (Exception e) {
                log.error("[ALERT] RabbitMQ publish FAILED for env={}, ns={}, version={}, operation={}. "
                        + "Falling back to local-only notification. Multi-instance broadcast is BROKEN. "
                        + "Cause: {}",
                        environment, namespace, version, operation, e.getMessage(), e);
                notificationService.notifyChange(environment, namespace);
            }
        } else {
            log.error("[ALERT] RabbitMQ is NOT available. Config changes (env={}, ns={}) "
                    + "are only broadcast to local instance. Other server nodes will NOT receive updates. "
                    + "Check spring.rabbitmq.host configuration and RabbitMQ service status.",
                    environment, namespace);
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
        dto.setEncrypted(item.getEncrypted());
        dto.setCreatedAt(item.getCreatedAt());
        dto.setUpdatedAt(item.getUpdatedAt());
        dto.setLastPulledAt(item.getLastPulledAt());
        dto.setZombie(item.getZombie());
        return dto;
    }
}
