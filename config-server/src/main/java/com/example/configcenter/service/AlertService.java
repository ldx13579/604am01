package com.example.configcenter.service;

import com.example.configcenter.metrics.MetricsService;
import com.example.configcenter.model.entity.AlertHistory;
import com.example.configcenter.model.entity.AlertRule;
import com.example.configcenter.repository.AlertHistoryRepository;
import com.example.configcenter.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRuleRepository alertRuleRepo;
    private final AlertHistoryRepository alertHistoryRepo;
    private final Map<String, Supplier<Double>> metricProviders = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private NotificationService notificationService;

    public AlertService(AlertRuleRepository alertRuleRepo,
                        AlertHistoryRepository alertHistoryRepo) {
        this.alertRuleRepo = alertRuleRepo;
        this.alertHistoryRepo = alertHistoryRepo;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void registerMetricProvider(String metricName, Supplier<Double> provider) {
        metricProviders.put(metricName, provider);
    }

    @Scheduled(fixedDelayString = "${alert.check.interval-ms:30000}")
    @Transactional
    public void checkAlerts() {
        List<AlertRule> rules = alertRuleRepo.findByEnabledTrue();
        for (AlertRule rule : rules) {
            try {
                Double currentValue = getMetricValue(rule.getMetricName());
                if (currentValue == null) continue;

                if (isThresholdBreached(currentValue, rule.getOperator(), rule.getThreshold())) {
                    if (isInCooldown(rule)) continue;
                    triggerAlert(rule, currentValue);
                }
            } catch (Exception e) {
                log.error("Error checking alert rule {}: {}", rule.getRuleName(), e.getMessage());
            }
        }
    }

    private Double getMetricValue(String metricName) {
        Supplier<Double> provider = metricProviders.get(metricName);
        if (provider != null) {
            return provider.get();
        }
        switch (metricName) {
            case "polling.active_clients":
                return notificationService != null ? (double) notificationService.getActiveHolderCount() : null;
            case "polling.failure_rate":
                return 0.0;
            case "zombie.count":
                return null;
            default:
                return null;
        }
    }

    private boolean isThresholdBreached(double value, String operator, double threshold) {
        return switch (operator) {
            case "GT", ">" -> value > threshold;
            case "GTE", ">=" -> value >= threshold;
            case "LT", "<" -> value < threshold;
            case "LTE", "<=" -> value <= threshold;
            case "EQ", "==" -> value == threshold;
            case "NEQ", "!=" -> value != threshold;
            default -> false;
        };
    }

    private boolean isInCooldown(AlertRule rule) {
        if (rule.getLastTriggeredAt() == null) return false;
        return rule.getLastTriggeredAt()
                .plusMinutes(rule.getCooldownMinutes())
                .isAfter(LocalDateTime.now());
    }

    @Transactional
    public void triggerAlert(AlertRule rule, double currentValue) {
        rule.setLastTriggeredAt(LocalDateTime.now());
        rule.setTriggerCount(rule.getTriggerCount() + 1);
        alertRuleRepo.save(rule);

        AlertHistory history = new AlertHistory();
        history.setAlertRuleId(rule.getId());
        history.setRuleName(rule.getRuleName());
        history.setMetricName(rule.getMetricName());
        history.setMetricValue(currentValue);
        history.setThreshold(rule.getThreshold());
        history.setSeverity(rule.getSeverity());
        history.setNotifyChannels(rule.getNotifyChannels());

        String[] channels = rule.getNotifyChannels().split(",");
        StringBuilder errors = new StringBuilder();

        for (String channel : channels) {
            try {
                sendNotification(channel.trim(), rule, currentValue);
            } catch (Exception e) {
                errors.append(channel).append(": ").append(e.getMessage()).append("; ");
            }
        }

        if (errors.length() > 0) {
            history.setNotifyStatus("PARTIAL_FAILURE");
            history.setErrorMessage(errors.toString());
        } else {
            history.setNotifyStatus("SUCCESS");
        }

        alertHistoryRepo.save(history);
    }

    private void sendNotification(String channel, AlertRule rule, double currentValue) {
        String message = formatAlertMessage(rule, currentValue);

        switch (channel.toUpperCase()) {
            case "LOG" -> sendLogNotification(rule, message);
            case "WEBHOOK" -> sendWebhookNotification(rule, message);
            case "EMAIL" -> sendEmailNotification(rule, message);
            default -> log.warn("Unknown notification channel: {}", channel);
        }
    }

    private void sendLogNotification(AlertRule rule, String message) {
        switch (rule.getSeverity()) {
            case "CRITICAL" -> log.error("[ALERT-CRITICAL] {}", message);
            case "WARNING" -> log.warn("[ALERT-WARNING] {}", message);
            default -> log.info("[ALERT-INFO] {}", message);
        }
    }

    private void sendWebhookNotification(AlertRule rule, String message) {
        if (rule.getWebhookUrl() == null || rule.getWebhookUrl().isBlank()) {
            log.warn("Webhook URL not configured for rule: {}", rule.getRuleName());
            return;
        }

        String payload = String.format(
                "{\"alertName\":\"%s\",\"severity\":\"%s\",\"metric\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                rule.getRuleName(), rule.getSeverity(), rule.getMetricName(),
                message.replace("\"", "\\\""), LocalDateTime.now());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rule.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Webhook returned status " + response.statusCode());
            }
            log.info("Webhook notification sent for rule: {}", rule.getRuleName());
        } catch (Exception e) {
            throw new RuntimeException("Webhook failed: " + e.getMessage(), e);
        }
    }

    private void sendEmailNotification(AlertRule rule, String message) {
        if (rule.getEmailTo() == null || rule.getEmailTo().isBlank()) {
            log.warn("Email recipients not configured for rule: {}", rule.getRuleName());
            return;
        }
        log.info("[EMAIL-ALERT] To: {}, Subject: [{}] {}, Body: {}",
                rule.getEmailTo(), rule.getSeverity(), rule.getRuleName(), message);
    }

    private String formatAlertMessage(AlertRule rule, double currentValue) {
        return String.format("[%s] %s: 指标 %s 当前值 %.2f %s 阈值 %.2f",
                rule.getSeverity(), rule.getRuleName(),
                rule.getMetricName(), currentValue,
                rule.getOperator(), rule.getThreshold());
    }

    public List<AlertRule> listRules() {
        return alertRuleRepo.findAll();
    }

    public AlertRule createRule(AlertRule rule) {
        return alertRuleRepo.save(rule);
    }

    public AlertRule updateRule(Long id, AlertRule updated) {
        AlertRule rule = alertRuleRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert rule not found: " + id));
        rule.setRuleName(updated.getRuleName());
        rule.setMetricName(updated.getMetricName());
        rule.setOperator(updated.getOperator());
        rule.setThreshold(updated.getThreshold());
        rule.setSeverity(updated.getSeverity());
        rule.setNotifyChannels(updated.getNotifyChannels());
        rule.setWebhookUrl(updated.getWebhookUrl());
        rule.setEmailTo(updated.getEmailTo());
        rule.setEnabled(updated.getEnabled());
        rule.setCooldownMinutes(updated.getCooldownMinutes());
        return alertRuleRepo.save(rule);
    }

    public void deleteRule(Long id) {
        alertRuleRepo.deleteById(id);
    }

    public List<AlertHistory> getAlertHistory() {
        return alertHistoryRepo.findTop50ByOrderByCreatedAtDesc();
    }
}
