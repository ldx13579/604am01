package com.example.configcenter.controller;

import com.example.configcenter.model.entity.ConfigChangeTestLog;
import com.example.configcenter.repository.ConfigChangeTestLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/change-tests")
public class ConfigChangeTestController {

    private final ConfigChangeTestLogRepository testLogRepo;

    public ConfigChangeTestController(ConfigChangeTestLogRepository testLogRepo) {
        this.testLogRepo = testLogRepo;
    }

    @GetMapping("/{configItemId}")
    public List<ConfigChangeTestLog> getTestLogs(@PathVariable Long configItemId) {
        return testLogRepo.findByConfigItemIdOrderByCreatedAtDesc(configItemId);
    }

    @GetMapping("/failures")
    public List<ConfigChangeTestLog> getRecentFailures() {
        return testLogRepo.findTop20ByTestResultOrderByCreatedAtDesc("FAIL");
    }
}
