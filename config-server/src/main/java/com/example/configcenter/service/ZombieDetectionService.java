package com.example.configcenter.service;

import com.example.configcenter.metrics.MetricsService;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.model.entity.ZombieCleanupLog;
import com.example.configcenter.repository.ConfigItemRepository;
import com.example.configcenter.repository.ConfigPullRecordRepository;
import com.example.configcenter.repository.ZombieCleanupLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ZombieDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ZombieDetectionService.class);

    @Value("${zombie.detection.threshold-days:30}")
    private int thresholdDays;

    @Value("${zombie.cleanup.grace-period-days:60}")
    private int gracePeriodDays;

    @Value("${zombie.cleanup.auto-enabled:true}")
    private boolean autoCleanupEnabled;

    @Value("${zombie.cleanup.action:ARCHIVED}")
    private String defaultCleanupAction;

    private final ConfigItemRepository configItemRepo;
    private final ConfigPullRecordRepository pullRecordRepo;
    private final ZombieCleanupLogRepository cleanupLogRepo;

    @Autowired(required = false)
    private MetricsService metricsService;

    public ZombieDetectionService(ConfigItemRepository configItemRepo,
                                   ConfigPullRecordRepository pullRecordRepo,
                                   ZombieCleanupLogRepository cleanupLogRepo) {
        this.configItemRepo = configItemRepo;
        this.pullRecordRepo = pullRecordRepo;
        this.cleanupLogRepo = cleanupLogRepo;
    }

    @Transactional
    public void recordPull(String environment, String namespace, String clientIp) {
        configItemRepo.updateLastPulledAt(environment, namespace, LocalDateTime.now());
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void detectZombies() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(thresholdDays);
        List<ConfigItem> candidates = configItemRepo.findByLastPulledAtBeforeOrLastPulledAtIsNull(threshold);

        int zombieCount = 0;
        for (ConfigItem item : candidates) {
            if (!Boolean.TRUE.equals(item.getZombie())) {
                item.setZombie(true);
                configItemRepo.save(item);
            }
            zombieCount++;
        }

        List<ConfigItem> recentlyPulled = configItemRepo.findAll().stream()
                .filter(item -> item.getLastPulledAt() != null && item.getLastPulledAt().isAfter(threshold))
                .filter(item -> Boolean.TRUE.equals(item.getZombie()))
                .toList();
        for (ConfigItem item : recentlyPulled) {
            item.setZombie(false);
            configItemRepo.save(item);
        }

        if (metricsService != null) {
            metricsService.setZombieCount(zombieCount);
        }

        log.info("Zombie detection completed: {} zombie configs found", zombieCount);

        pullRecordRepo.deleteByPulledAtBefore(LocalDateTime.now().minusDays(90));
    }

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void autoCleanupZombies() {
        if (!autoCleanupEnabled) {
            log.info("Auto-cleanup disabled, skipping");
            return;
        }

        LocalDateTime gracePeriodThreshold = LocalDateTime.now().minusDays(gracePeriodDays);
        List<ConfigItem> expiredZombies = configItemRepo.findAll().stream()
                .filter(item -> Boolean.TRUE.equals(item.getZombie()))
                .filter(item -> {
                    LocalDateTime lastActivity = item.getLastPulledAt() != null
                            ? item.getLastPulledAt() : item.getCreatedAt();
                    return lastActivity.isBefore(gracePeriodThreshold);
                })
                .toList();

        int cleaned = 0;
        for (ConfigItem item : expiredZombies) {
            try {
                ZombieCleanupLog cleanupLog = new ZombieCleanupLog();
                cleanupLog.setConfigItemId(item.getId());
                cleanupLog.setConfigKey(item.getConfigKey());
                cleanupLog.setEnvironment(item.getEnvironment());
                cleanupLog.setNamespace(item.getNamespace());
                cleanupLog.setLastPulledAt(item.getLastPulledAt());
                cleanupLog.setCleanupAction(defaultCleanupAction);
                cleanupLogRepo.save(cleanupLog);

                if ("DELETED".equals(defaultCleanupAction)) {
                    configItemRepo.delete(item);
                } else {
                    item.setDescription("[ARCHIVED] " + item.getDescription());
                    item.setConfigValue("__ARCHIVED__:" + item.getConfigValue());
                    configItemRepo.save(item);
                }
                cleaned++;
            } catch (Exception e) {
                log.error("Failed to cleanup zombie config {}: {}", item.getConfigKey(), e.getMessage());
            }
        }

        log.info("Zombie auto-cleanup completed: {}/{} configs cleaned (action={})",
                cleaned, expiredZombies.size(), defaultCleanupAction);
    }

    public List<ConfigItemDTO> getZombieConfigs(String environment, String namespace) {
        return configItemRepo.findByEnvironmentAndNamespaceAndZombieTrue(environment, namespace)
                .stream().map(this::toDTO).toList();
    }

    public List<ZombieCleanupLog> getCleanupHistory() {
        return cleanupLogRepo.findTop50ByOrderByCreatedAtDesc();
    }

    public boolean shouldCleanup(ConfigItem item) {
        if (!Boolean.TRUE.equals(item.getZombie())) return false;
        LocalDateTime lastActivity = item.getLastPulledAt() != null
                ? item.getLastPulledAt() : item.getCreatedAt();
        return lastActivity.isBefore(LocalDateTime.now().minusDays(gracePeriodDays));
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
