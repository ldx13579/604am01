package com.example.configcenter.controller;

import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.PollingResponse;
import com.example.configcenter.metrics.MetricsService;
import com.example.configcenter.service.ConfigService;
import com.example.configcenter.service.GrayscaleService;
import com.example.configcenter.service.NotificationService;
import com.example.configcenter.service.ZombieDetectionService;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LongPollingController {

    private static final long POLLING_TIMEOUT = 30000L;

    private final ConfigService configService;
    private final NotificationService notificationService;
    private final GrayscaleService grayscaleService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private ZombieDetectionService zombieDetectionService;

    public LongPollingController(ConfigService configService,
                                 NotificationService notificationService,
                                 GrayscaleService grayscaleService) {
        this.configService = configService;
        this.notificationService = notificationService;
        this.grayscaleService = grayscaleService;
    }

    @GetMapping("/polling")
    public DeferredResult<PollingResponse> polling(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns,
            @RequestParam Long clientVersion,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        grayscaleService.registerHeartbeat(clientIp, env, ns, clientVersion);

        Long grayscaleVersion = grayscaleService.getGrayscaleTargetVersion(clientIp, env, ns);
        Long serverVersion = (grayscaleVersion != null) ? grayscaleVersion : configService.getCurrentVersion(env, ns);

        Timer.Sample timerSample = (metricsService != null) ? metricsService.startPollingTimer() : null;

        DeferredResult<PollingResponse> result = new DeferredResult<>(POLLING_TIMEOUT);

        if (serverVersion > clientVersion) {
            List<ConfigItemDTO> configs = configService.listConfigs(env, ns);
            result.setResult(PollingResponse.changed(serverVersion, configs));
            if (timerSample != null) metricsService.stopPollingTimer(timerSample);
            if (zombieDetectionService != null) zombieDetectionService.recordPull(env, ns, clientIp);
            return result;
        }

        if (metricsService != null) metricsService.incrementActiveClients();

        result.onTimeout(() -> {
            result.setResult(PollingResponse.noChange(serverVersion));
            if (metricsService != null) {
                metricsService.stopPollingTimer(timerSample);
                metricsService.decrementActiveClients();
            }
        });

        result.onCompletion(() -> {
            if (metricsService != null && !result.hasResult()) {
                metricsService.decrementActiveClients();
            }
        });

        result.onError(throwable -> {
            if (metricsService != null) {
                metricsService.recordPollingFailure();
                metricsService.decrementActiveClients();
            }
        });

        notificationService.addHolder(env, ns, result);

        return result;
    }

    @GetMapping("/version")
    public Long getCurrentVersion(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return configService.getCurrentVersion(env, ns);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
