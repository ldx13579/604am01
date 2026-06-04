package com.example.configcenter.controller;

import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.PollingResponse;
import com.example.configcenter.service.ConfigService;
import com.example.configcenter.service.NotificationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LongPollingController {

    private static final long POLLING_TIMEOUT = 30000L;

    private final ConfigService configService;
    private final NotificationService notificationService;

    public LongPollingController(ConfigService configService, NotificationService notificationService) {
        this.configService = configService;
        this.notificationService = notificationService;
    }

    @GetMapping("/polling")
    public DeferredResult<PollingResponse> polling(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns,
            @RequestParam Long clientVersion) {

        Long serverVersion = configService.getCurrentVersion(env, ns);

        DeferredResult<PollingResponse> result = new DeferredResult<>(POLLING_TIMEOUT);

        if (serverVersion > clientVersion) {
            List<ConfigItemDTO> configs = configService.listConfigs(env, ns);
            result.setResult(PollingResponse.changed(serverVersion, configs));
            return result;
        }

        result.onTimeout(() -> result.setResult(PollingResponse.noChange(serverVersion)));

        notificationService.addHolder(env, ns, result);

        return result;
    }

    @GetMapping("/version")
    public Long getCurrentVersion(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return configService.getCurrentVersion(env, ns);
    }
}
