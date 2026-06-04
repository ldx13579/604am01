package com.example.configcenter.controller;

import com.example.configcenter.model.dto.ClientInstanceDTO;
import com.example.configcenter.model.dto.GrayscaleRuleRequest;
import com.example.configcenter.model.entity.GrayscaleRule;
import com.example.configcenter.service.GrayscaleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grayscale")
public class GrayscaleController {

    private final GrayscaleService grayscaleService;

    public GrayscaleController(GrayscaleService grayscaleService) {
        this.grayscaleService = grayscaleService;
    }

    @GetMapping("/rules")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #env, #ns, 'VIEWER')")
    public List<GrayscaleRule> listRules(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return grayscaleService.listRules(env, ns);
    }

    @PostMapping("/rules")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #request.environment, #request.namespace, 'DEVELOPER')")
    public ResponseEntity<GrayscaleRule> createRule(@Valid @RequestBody GrayscaleRuleRequest request) {
        GrayscaleRule rule = grayscaleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #request.environment, #request.namespace, 'DEVELOPER')")
    public GrayscaleRule updateRule(@PathVariable Long id, @Valid @RequestBody GrayscaleRuleRequest request) {
        return grayscaleService.updateRule(id, request);
    }

    @PostMapping("/rules/{id}/full-release")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, null, null, 'ADMIN')")
    public GrayscaleRule fullRelease(@PathVariable Long id) {
        return grayscaleService.fullRelease(id);
    }

    @PostMapping("/rules/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, null, null, 'ADMIN')")
    public GrayscaleRule cancelRule(@PathVariable Long id) {
        return grayscaleService.cancelRule(id);
    }

    @GetMapping("/clients")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #env, #ns, 'VIEWER')")
    public List<ClientInstanceDTO> listClients(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return grayscaleService.listClients(env, ns);
    }
}
