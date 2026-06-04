package com.example.configcenter.service;

import com.example.configcenter.model.entity.AlertEscalationPolicy;
import com.example.configcenter.model.entity.AlertRule;
import com.example.configcenter.model.entity.AlertSubscriber;
import com.example.configcenter.repository.AlertEscalationPolicyRepository;
import com.example.configcenter.repository.AlertSubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class AlertEscalationService {

    private static final Logger log = LoggerFactory.getLogger(AlertEscalationService.class);

    private final AlertEscalationPolicyRepository policyRepo;
    private final AlertSubscriberRepository subscriberRepo;
    private final HttpClient httpClient;

    public AlertEscalationService(AlertEscalationPolicyRepository policyRepo,
                                   AlertSubscriberRepository subscriberRepo) {
        this.policyRepo = policyRepo;
        this.subscriberRepo = subscriberRepo;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Route an alert through the escalation chain based on severity.
     * Finds matching policies, resolves subscribers per role group,
     * and dispatches notifications through each subscriber's preferred channels.
     */
    public List<NotificationResult> routeAlert(AlertRule rule, double metricValue) {
        String severity = rule.getSeverity();
        List<AlertEscalationPolicy> policies =
                policyRepo.findBySeverityAndEnabledTrueOrderByEscalationLevelAsc(severity);

        if (policies.isEmpty()) {
            log.warn("No escalation policy found for severity={}, falling back to default LOG", severity);
            return List.of(new NotificationResult("LOG", "SYSTEM", true, null));
        }

        List<NotificationResult> results = new ArrayList<>();

        for (AlertEscalationPolicy policy : policies) {
            List<AlertSubscriber> subscribers =
                    subscriberRepo.findByRoleGroupAndEnabledTrue(policy.getRoleGroup());

            for (AlertSubscriber subscriber : subscribers) {
                if (isInQuietHours(subscriber)) {
                    if (!"CRITICAL".equals(severity)) {
                        results.add(new NotificationResult(
                                "SKIPPED", subscriber.getName(), true,
                                "In quiet hours, severity not CRITICAL"));
                        continue;
                    }
                }

                String[] channels = resolveChannels(policy, subscriber);
                for (String channel : channels) {
                    boolean success = dispatchToSubscriber(channel, subscriber, rule, metricValue);
                    results.add(new NotificationResult(
                            channel, subscriber.getName(), success,
                            success ? null : "Delivery failed"));
                }
            }
        }

        log.info("Alert routed: severity={}, policies={}, notifications={}",
                severity, policies.size(), results.size());
        return results;
    }

    private String[] resolveChannels(AlertEscalationPolicy policy, AlertSubscriber subscriber) {
        String policyChannels = policy.getNotifyChannels();
        String subscriberChannels = subscriber.getPreferredChannels();

        List<String> resolved = new ArrayList<>();
        for (String ch : policyChannels.split(",")) {
            String trimmed = ch.trim().toUpperCase();
            if (subscriberChannels.toUpperCase().contains(trimmed)) {
                resolved.add(trimmed);
            }
        }

        if (resolved.isEmpty()) {
            resolved.add("LOG");
        }
        return resolved.toArray(new String[0]);
    }

    private boolean dispatchToSubscriber(String channel, AlertSubscriber subscriber,
                                          AlertRule rule, double metricValue) {
        String message = formatMessage(rule, metricValue, subscriber);

        try {
            switch (channel) {
                case "LOG" -> {
                    log.info("[ESCALATION→{}] [{}] {}", subscriber.getName(), rule.getSeverity(), message);
                    return true;
                }
                case "WEBHOOK" -> {
                    return sendWebhook(subscriber.getWebhookUrl(), rule, metricValue, subscriber);
                }
                case "EMAIL" -> {
                    log.info("[EMAIL→{}] To: {}, Subject: [{}] {}, Body: {}",
                            subscriber.getName(), subscriber.getEmail(),
                            rule.getSeverity(), rule.getRuleName(), message);
                    return true;
                }
                case "SMS" -> {
                    log.info("[SMS→{}] Phone: {}, Message: {}",
                            subscriber.getName(), subscriber.getPhoneNumber(), message);
                    return true;
                }
                default -> {
                    log.warn("Unknown channel: {}", channel);
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Failed to dispatch to {} via {}: {}", subscriber.getName(), channel, e.getMessage());
            return false;
        }
    }

    private boolean sendWebhook(String url, AlertRule rule, double metricValue, AlertSubscriber subscriber) {
        if (url == null || url.isBlank()) return false;

        String payload = String.format(
                "{\"alert\":\"%s\",\"severity\":\"%s\",\"metric\":\"%s\",\"value\":%.2f,"
                + "\"threshold\":%.2f,\"subscriber\":\"%s\",\"role\":\"%s\",\"timestamp\":\"%s\"}",
                rule.getRuleName(), rule.getSeverity(), rule.getMetricName(),
                metricValue, rule.getThreshold(),
                subscriber.getName(), subscriber.getRoleGroup(),
                LocalDateTime.now().toString());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-Alert-Severity", rule.getSeverity())
                    .header("X-Alert-Role", subscriber.getRoleGroup())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 400;
        } catch (Exception e) {
            log.error("Webhook dispatch failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isInQuietHours(AlertSubscriber subscriber) {
        if (subscriber.getQuietHoursStart() == null || subscriber.getQuietHoursEnd() == null) {
            return false;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(subscriber.getQuietHoursStart(), DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(subscriber.getQuietHoursEnd(), DateTimeFormatter.ofPattern("HH:mm"));

            if (start.isBefore(end)) {
                return now.isAfter(start) && now.isBefore(end);
            } else {
                return now.isAfter(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String formatMessage(AlertRule rule, double metricValue, AlertSubscriber subscriber) {
        return String.format("[%s] %s: 指标[%s] 当前值=%.2f, 阈值%s%.2f | 接收人: %s (%s)",
                rule.getSeverity(), rule.getRuleName(), rule.getMetricName(),
                metricValue, rule.getOperator(), rule.getThreshold(),
                subscriber.getName(), subscriber.getRoleGroup());
    }

    // ==================== CRUD ====================

    public List<AlertEscalationPolicy> listPolicies() {
        return policyRepo.findByEnabledTrueOrderBySeverityAscEscalationLevelAsc();
    }

    public AlertEscalationPolicy createPolicy(AlertEscalationPolicy policy) {
        return policyRepo.save(policy);
    }

    public void deletePolicy(Long id) {
        policyRepo.deleteById(id);
    }

    public List<AlertSubscriber> listSubscribers() {
        return subscriberRepo.findByEnabledTrue();
    }

    public AlertSubscriber createSubscriber(AlertSubscriber subscriber) {
        return subscriberRepo.save(subscriber);
    }

    public AlertSubscriber updateSubscriber(Long id, AlertSubscriber updated) {
        AlertSubscriber sub = subscriberRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscriber not found: " + id));
        sub.setName(updated.getName());
        sub.setRoleGroup(updated.getRoleGroup());
        sub.setEmail(updated.getEmail());
        sub.setWebhookUrl(updated.getWebhookUrl());
        sub.setPhoneNumber(updated.getPhoneNumber());
        sub.setPreferredChannels(updated.getPreferredChannels());
        sub.setQuietHoursStart(updated.getQuietHoursStart());
        sub.setQuietHoursEnd(updated.getQuietHoursEnd());
        sub.setEnabled(updated.getEnabled());
        return subscriberRepo.save(sub);
    }

    public void deleteSubscriber(Long id) {
        subscriberRepo.deleteById(id);
    }

    public record NotificationResult(String channel, String subscriber, boolean success, String error) {}
}
