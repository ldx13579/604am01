package com.example.configcenter.controller;

import com.example.configcenter.model.entity.SecurityPublicPath;
import com.example.configcenter.service.SecurityPathService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security/public-paths")
public class SecurityPathController {

    private final SecurityPathService securityPathService;

    public SecurityPathController(SecurityPathService securityPathService) {
        this.securityPathService = securityPathService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public List<SecurityPublicPath> listAll() {
        return securityPathService.listAll();
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<SecurityPublicPath> create(@RequestBody Map<String, String> request) {
        String pathPattern = request.get("pathPattern");
        String description = request.getOrDefault("description", "");
        if (pathPattern == null || pathPattern.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        SecurityPublicPath created = securityPathService.create(pathPattern, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<SecurityPublicPath> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        String pathPattern = (String) request.get("pathPattern");
        String description = (String) request.get("description");
        Boolean enabled = request.containsKey("enabled") ? (Boolean) request.get("enabled") : null;
        SecurityPublicPath updated = securityPathService.update(id, pathPattern, description, enabled);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        securityPathService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Map<String, String>> refreshCache() {
        securityPathService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "Public paths cache refreshed"));
    }
}
