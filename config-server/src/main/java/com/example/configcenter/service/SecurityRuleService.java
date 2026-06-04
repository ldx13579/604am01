package com.example.configcenter.service;

import com.example.configcenter.model.entity.SecurityRule;
import com.example.configcenter.repository.SecurityRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SecurityRuleService {

    private final SecurityRuleRepository repository;

    private volatile Set<String> cachedBlockedKeywords = Set.of();
    private volatile Pattern cachedSuspiciousPattern = Pattern.compile("(?!)");
    private final AtomicLong lastRefresh = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 30_000;

    public SecurityRuleService(SecurityRuleRepository repository) {
        this.repository = repository;
    }

    public Set<String> getBlockedKeywords() {
        refreshCacheIfNeeded();
        return cachedBlockedKeywords;
    }

    public Pattern getSuspiciousPattern() {
        refreshCacheIfNeeded();
        return cachedSuspiciousPattern;
    }

    public List<SecurityRule> listAll() {
        return repository.findAll();
    }

    public List<SecurityRule> listEnabled() {
        return repository.findByEnabledTrue();
    }

    @Transactional
    public SecurityRule create(SecurityRule rule) {
        SecurityRule saved = repository.save(rule);
        refreshCache();
        return saved;
    }

    @Transactional
    public SecurityRule update(Long id, SecurityRule updated) {
        SecurityRule existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Security rule not found: " + id));
        if (updated.getRuleType() != null) existing.setRuleType(updated.getRuleType());
        if (updated.getRuleValue() != null) existing.setRuleValue(updated.getRuleValue());
        if (updated.getDescription() != null) existing.setDescription(updated.getDescription());
        if (updated.getSeverity() != null) existing.setSeverity(updated.getSeverity());
        if (updated.getEnabled() != null) existing.setEnabled(updated.getEnabled());
        SecurityRule saved = repository.save(existing);
        refreshCache();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        refreshCache();
    }

    public void refreshCache() {
        List<SecurityRule> rules = repository.findByEnabledTrue();

        cachedBlockedKeywords = rules.stream()
                .filter(r -> "BLOCKED_KEYWORD".equals(r.getRuleType()))
                .map(SecurityRule::getRuleValue)
                .collect(Collectors.toSet());

        List<String> patterns = rules.stream()
                .filter(r -> "SUSPICIOUS_PATTERN".equals(r.getRuleType()))
                .map(SecurityRule::getRuleValue)
                .toList();

        if (patterns.isEmpty()) {
            cachedSuspiciousPattern = Pattern.compile("(?!)");
        } else {
            String combined = String.join("|", patterns);
            cachedSuspiciousPattern = Pattern.compile(combined);
        }

        lastRefresh.set(System.currentTimeMillis());
    }

    private void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() - lastRefresh.get() > CACHE_TTL_MS) {
            refreshCache();
        }
    }
}
