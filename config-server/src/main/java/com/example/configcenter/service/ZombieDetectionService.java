package com.example.configcenter.service;

import com.example.configcenter.metrics.MetricsService;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.repository.ConfigItemRepository;
import com.example.configcenter.repository.ConfigPullRecordRepository;
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

    private final ConfigItemRepository configItemRepo;
    private final ConfigPullRecordRepository pullRecordRepo;

    @Autowired(required = false)
    private MetricsService metricsService;

    public ZombieDetectionService(ConfigItemRepository configItemRepo,
                                   ConfigPullRecordRepository pullRecordRepo) {
        this.configItemRepo = configItemRepo;
        this.pullRecordRepo = pullRecordRepo;
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

        // Cleanup old pull records (keep 90 days)
        pullRecordRepo.deleteByPulledAtBefore(LocalDateTime.now().minusDays(90));
    }

    public List<ConfigItemDTO> getZombieConfigs(String environment, String namespace) {
        return configItemRepo.findByEnvironmentAndNamespaceAndZombieTrue(environment, namespace)
                .stream().map(this::toDTO).toList();
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
