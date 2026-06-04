package com.example.configcenter.controller;

import com.example.configcenter.model.entity.CleanupPolicy;
import com.example.configcenter.service.CleanupPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cleanup-policies")
public class CleanupPolicyController {

    private final CleanupPolicyService cleanupPolicyService;

    public CleanupPolicyController(CleanupPolicyService cleanupPolicyService) {
        this.cleanupPolicyService = cleanupPolicyService;
    }

    @GetMapping
    public List<CleanupPolicy> listPolicies() {
        return cleanupPolicyService.listPolicies();
    }

    @PostMapping
    public CleanupPolicy createPolicy(@RequestBody CleanupPolicy policy) {
        return cleanupPolicyService.createPolicy(policy);
    }

    @PutMapping("/{id}")
    public CleanupPolicy updatePolicy(@PathVariable Long id, @RequestBody CleanupPolicy policy) {
        return cleanupPolicyService.updatePolicy(id, policy);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        cleanupPolicyService.deletePolicy(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/apply")
    public Map<String, Object> applyPolicies() {
        int cleaned = cleanupPolicyService.applyPolicies();
        return Map.of("cleaned", cleaned, "status", "OK");
    }
}
