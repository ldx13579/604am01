package com.example.configcenter.controller;

import com.example.configcenter.model.dto.*;
import com.example.configcenter.service.ValidationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/validation")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @GetMapping("/scripts")
    public List<ValidationScriptDTO> listScripts(
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        return validationService.listScripts(env, ns);
    }

    @PostMapping("/scripts")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #request.environment, #request.namespace != null ? #request.namespace : 'default', 'DEVELOPER')")
    public ResponseEntity<ValidationScriptDTO> createScript(@Valid @RequestBody ValidationScriptRequest request) {
        ValidationScriptDTO created = validationService.createScript(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/scripts/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #request.environment, #request.namespace != null ? #request.namespace : 'default', 'DEVELOPER')")
    public ValidationScriptDTO updateScript(@PathVariable Long id, @Valid @RequestBody ValidationScriptRequest request) {
        return validationService.updateScript(id, request);
    }

    @DeleteMapping("/scripts/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, #env, #ns, 'ADMIN')")
    public ResponseEntity<Void> deleteScript(
            @PathVariable Long id,
            @RequestParam(defaultValue = "dev") String env,
            @RequestParam(defaultValue = "default") String ns) {
        validationService.deleteScript(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, 'dev', 'default', 'DEVELOPER')")
    public ValidationResult testScript(@Valid @RequestBody ValidationTestRequest request) {
        return validationService.testScript(request);
    }
}
