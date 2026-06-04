package com.example.configcenter.controller;

import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.service.ConfigService;
import com.example.configcenter.service.ZombieDetectionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zombies")
public class ZombieController {

    private final ZombieDetectionService zombieDetectionService;
    private final ConfigService configService;

    public ZombieController(ZombieDetectionService zombieDetectionService,
                            ConfigService configService) {
        this.zombieDetectionService = zombieDetectionService;
        this.configService = configService;
    }

    @GetMapping
    public List<ConfigItemDTO> listZombies(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return zombieDetectionService.getZombieConfigs(env, ns);
    }

    @DeleteMapping("/{id}")
    public void cleanup(@PathVariable Long id) {
        configService.deleteConfig(id);
    }

    @PostMapping("/detect")
    public void triggerDetection() {
        zombieDetectionService.detectZombies();
    }
}
