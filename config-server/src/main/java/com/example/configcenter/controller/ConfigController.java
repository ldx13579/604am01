package com.example.configcenter.controller;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.ConfigVersion;
import com.example.configcenter.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #env, #ns, 'VIEWER')")
    public List<ConfigItemDTO> listConfigs(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return configService.listConfigs(env, ns);
    }

    @GetMapping("/{id}")
    public ConfigItemDTO getConfig(@PathVariable Long id) {
        return configService.getConfig(id);
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #request.environment, #request.namespace, 'DEVELOPER')")
    public ResponseEntity<ConfigItemDTO> createConfig(@Valid @RequestBody ConfigCreateRequest request) {
        ConfigItemDTO created = configService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, null, null, 'DEVELOPER')")
    public ConfigItemDTO updateConfig(@PathVariable Long id, @Valid @RequestBody ConfigUpdateRequest request) {
        return configService.updateConfig(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, null, null, 'DEVELOPER')")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<ConfigVersion> getVersionHistory(@PathVariable Long id) {
        return configService.getVersionHistory(id);
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, null, null, 'DEVELOPER')")
    public ConfigItemDTO rollback(@PathVariable Long id, @RequestParam Long targetVersion) {
        return configService.rollback(id, targetVersion);
    }
}
