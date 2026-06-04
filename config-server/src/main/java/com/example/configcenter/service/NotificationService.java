package com.example.configcenter.service;

import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.PollingResponse;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.repository.ConfigItemRepository;
import com.example.configcenter.repository.VersionCounterRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<DeferredResult<PollingResponse>>> holders =
            new ConcurrentHashMap<>();

    private final ConfigItemRepository configItemRepo;
    private final VersionCounterRepository versionCounterRepo;

    public NotificationService(ConfigItemRepository configItemRepo,
                               VersionCounterRepository versionCounterRepo) {
        this.configItemRepo = configItemRepo;
        this.versionCounterRepo = versionCounterRepo;
    }

    public void addHolder(String environment, String namespace, DeferredResult<PollingResponse> result) {
        String key = environment + ":" + namespace;
        holders.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(result);
        result.onCompletion(() -> removeHolder(key, result));
        result.onTimeout(() -> removeHolder(key, result));
    }

    public void notifyChange(String environment, String namespace) {
        String key = environment + ":" + namespace;
        CopyOnWriteArrayList<DeferredResult<PollingResponse>> list = holders.get(key);
        if (list == null || list.isEmpty()) return;

        Long version = versionCounterRepo.findByEnvironmentAndNamespace(environment, namespace)
                .map(vc -> vc.getCurrentVersion())
                .orElse(0L);

        List<ConfigItemDTO> configs = configItemRepo.findByEnvironmentAndNamespace(environment, namespace)
                .stream().map(this::toDTO).toList();

        PollingResponse response = PollingResponse.changed(version, configs);

        List<DeferredResult<PollingResponse>> snapshot = List.copyOf(list);
        for (DeferredResult<PollingResponse> result : snapshot) {
            if (!result.isSetOrExpired()) {
                result.setResult(response);
            }
            list.remove(result);
        }
    }

    @Scheduled(fixedDelayString = "${notification.cleanup.interval-ms:15000}")
    public void cleanupExpiredHolders() {
        for (Map.Entry<String, CopyOnWriteArrayList<DeferredResult<PollingResponse>>> entry : holders.entrySet()) {
            CopyOnWriteArrayList<DeferredResult<PollingResponse>> list = entry.getValue();
            if (list == null) continue;

            list.removeIf(DeferredResult::isSetOrExpired);

            if (list.isEmpty()) {
                holders.remove(entry.getKey(), list);
            }
        }
    }

    private void removeHolder(String key, DeferredResult<PollingResponse> result) {
        CopyOnWriteArrayList<DeferredResult<PollingResponse>> list = holders.get(key);
        if (list != null) {
            list.remove(result);
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
