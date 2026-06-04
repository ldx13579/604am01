package com.example.configcenter.controller;

import com.example.configcenter.model.entity.SecurityRule;
import com.example.configcenter.service.SecurityRuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/rules")
public class SecurityRuleController {

    private final SecurityRuleService securityRuleService;

    public SecurityRuleController(SecurityRuleService securityRuleService) {
        this.securityRuleService = securityRuleService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public List<SecurityRule> listAll() {
        return securityRuleService.listAll();
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<SecurityRule> create(@RequestBody SecurityRule rule) {
        if (rule.getRuleType() == null || rule.getRuleValue() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!isValidRuleType(rule.getRuleType())) {
            return ResponseEntity.badRequest().build();
        }
        SecurityRule created = securityRuleService.create(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<SecurityRule> update(@PathVariable Long id, @RequestBody SecurityRule rule) {
        if (rule.getRuleType() != null && !isValidRuleType(rule.getRuleType())) {
            return ResponseEntity.badRequest().build();
        }
        SecurityRule updated = securityRuleService.update(id, rule);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        securityRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Map<String, String>> refreshCache() {
        securityRuleService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "Security rules cache refreshed"));
    }

    private boolean isValidRuleType(String ruleType) {
        return "BLOCKED_KEYWORD".equals(ruleType)
                || "SUSPICIOUS_PATTERN".equals(ruleType)
                || "BLOCKED_FUNCTION".equals(ruleType);
    }
}
