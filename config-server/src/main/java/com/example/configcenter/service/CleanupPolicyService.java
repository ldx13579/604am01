package com.example.configcenter.service;

import com.example.configcenter.model.entity.CleanupPolicy;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.model.entity.ZombieCleanupLog;
import com.example.configcenter.repository.CleanupPolicyRepository;
import com.example.configcenter.repository.ConfigItemRepository;
import com.example.configcenter.repository.ZombieCleanupLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CleanupPolicyService {

    private static final Logger log = LoggerFactory.getLogger(CleanupPolicyService.class);

    private final CleanupPolicyRepository policyRepo;
    private final ConfigItemRepository configItemRepo;
    private final ZombieCleanupLogRepository cleanupLogRepo;

    public CleanupPolicyService(CleanupPolicyRepository policyRepo,
                                ConfigItemRepository configItemRepo,
                                ZombieCleanupLogRepository cleanupLogRepo) {
        this.policyRepo = policyRepo;
        this.configItemRepo = configItemRepo;
        this.cleanupLogRepo = cleanupLogRepo;
    }

    @Transactional
    public int applyPolicies() {
        List<CleanupPolicy> policies = policyRepo.findByEnabledTrueOrderByPriorityDesc();
        if (policies.isEmpty()) return 0;

        List<ConfigItem> allItems = configItemRepo.findAll();
        int totalCleaned = 0;

        for (ConfigItem item : allItems) {
            for (CleanupPolicy policy : policies) {
                if (matchesPolicy(item, policy)) {
                    executeCleanup(item, policy);
                    totalCleaned++;
                    break;
                }
            }
        }

        log.info("Cleanup policies applied: {} items cleaned by {} active policies",
                totalCleaned, policies.size());
        return totalCleaned;
    }

    public boolean matchesPolicy(ConfigItem item, CleanupPolicy policy) {
        boolean isAndLogic = "AND".equalsIgnoreCase(policy.getConditionLogic());
        boolean hasAnyCondition = false;
        boolean allMatch = true;
        boolean anyMatch = false;

        if (policy.getRequireZombie() != null && policy.getRequireZombie()) {
            hasAnyCondition = true;
            boolean zombieMatch = Boolean.TRUE.equals(item.getZombie());
            if (zombieMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getEnvironment() != null && !policy.getEnvironment().isBlank()) {
            hasAnyCondition = true;
            boolean envMatch = policy.getEnvironment().equalsIgnoreCase(item.getEnvironment());
            if (envMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getNamespace() != null && !policy.getNamespace().isBlank()) {
            hasAnyCondition = true;
            boolean nsMatch = policy.getNamespace().equalsIgnoreCase(item.getNamespace());
            if (nsMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getKeyPattern() != null && !policy.getKeyPattern().isBlank()) {
            hasAnyCondition = true;
            boolean keyMatch = matchesPattern(item.getConfigKey(), policy.getKeyPattern());
            if (keyMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getMinAgeDays() != null) {
            hasAnyCondition = true;
            LocalDateTime lastActivity = item.getLastPulledAt() != null
                    ? item.getLastPulledAt() : item.getCreatedAt();
            boolean ageMatch = lastActivity.isBefore(LocalDateTime.now().minusDays(policy.getMinAgeDays()));
            if (ageMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getMaxValueSizeBytes() != null) {
            hasAnyCondition = true;
            int size = item.getConfigValue() != null ? item.getConfigValue().getBytes().length : 0;
            boolean sizeMatch = size > policy.getMaxValueSizeBytes();
            if (sizeMatch) anyMatch = true; else allMatch = false;
        }

        if (policy.getMinValueSizeBytes() != null) {
            hasAnyCondition = true;
            int size = item.getConfigValue() != null ? item.getConfigValue().getBytes().length : 0;
            boolean sizeMatch = size < policy.getMinValueSizeBytes();
            if (sizeMatch) anyMatch = true; else allMatch = false;
        }

        if (!hasAnyCondition) return false;
        return isAndLogic ? allMatch : anyMatch;
    }

    private boolean matchesPattern(String key, String pattern) {
        String regex = pattern.replace("*", ".*").replace("?", ".");
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(key).matches();
    }

    private void executeCleanup(ConfigItem item, CleanupPolicy policy) {
        ZombieCleanupLog cleanupLog = new ZombieCleanupLog();
        cleanupLog.setConfigItemId(item.getId());
        cleanupLog.setConfigKey(item.getConfigKey());
        cleanupLog.setEnvironment(item.getEnvironment());
        cleanupLog.setNamespace(item.getNamespace());
        cleanupLog.setLastPulledAt(item.getLastPulledAt());
        cleanupLog.setCleanupAction(policy.getCleanupAction());
        cleanupLogRepo.save(cleanupLog);

        if ("DELETED".equals(policy.getCleanupAction())) {
            configItemRepo.delete(item);
        } else {
            item.setDescription("[ARCHIVED:" + policy.getPolicyName() + "] " + item.getDescription());
            item.setConfigValue("__ARCHIVED__:" + item.getConfigValue());
            configItemRepo.save(item);
        }
    }

    public List<CleanupPolicy> listPolicies() {
        return policyRepo.findAll();
    }

    public CleanupPolicy createPolicy(CleanupPolicy policy) {
        return policyRepo.save(policy);
    }

    public CleanupPolicy updatePolicy(Long id, CleanupPolicy updated) {
        CleanupPolicy policy = policyRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("CleanupPolicy not found: " + id));
        policy.setPolicyName(updated.getPolicyName());
        policy.setEnvironment(updated.getEnvironment());
        policy.setNamespace(updated.getNamespace());
        policy.setKeyPattern(updated.getKeyPattern());
        policy.setMinAgeDays(updated.getMinAgeDays());
        policy.setMaxValueSizeBytes(updated.getMaxValueSizeBytes());
        policy.setMinValueSizeBytes(updated.getMinValueSizeBytes());
        policy.setRequireZombie(updated.getRequireZombie());
        policy.setConditionLogic(updated.getConditionLogic());
        policy.setCleanupAction(updated.getCleanupAction());
        policy.setPriority(updated.getPriority());
        policy.setEnabled(updated.getEnabled());
        return policyRepo.save(policy);
    }

    public void deletePolicy(Long id) {
        policyRepo.deleteById(id);
    }
}
