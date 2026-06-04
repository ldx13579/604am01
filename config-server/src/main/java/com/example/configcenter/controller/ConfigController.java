package com.example.configcenter.controller;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.ConfigVersion;
import com.example.configcenter.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ConfigItemDTO> createConfig(@Valid @RequestBody ConfigCreateRequest request) {
        ConfigItemDTO created = configService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ConfigItemDTO updateConfig(@PathVariable Long id, @Valid @RequestBody ConfigUpdateRequest request) {
        return configService.updateConfig(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
        configService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    public List<ConfigVersion> getVersionHistory(@PathVariable Long id) {
        return configService.getVersionHistory(id);
    }

    @PostMapping("/{id}/rollback")
    public ConfigItemDTO rollback(@PathVariable Long id, @RequestParam Long targetVersion) {
        return configService.rollback(id, targetVersion);
    }
}
