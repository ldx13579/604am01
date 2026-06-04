package com.example.configcenter.service;

import com.example.configcenter.exception.ConfigNotFoundException;
import com.example.configcenter.model.dto.*;
import com.example.configcenter.model.entity.ValidationScript;
import com.example.configcenter.repository.ValidationScriptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ValidationServiceImpl implements ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationServiceImpl.class);

    private final ValidationScriptRepository scriptRepository;
    private final ScriptExecutor scriptExecutor;

    public ValidationServiceImpl(ValidationScriptRepository scriptRepository, ScriptExecutor scriptExecutor) {
        this.scriptRepository = scriptRepository;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public ValidationResult validate(String configKey, String configValue, String env, String ns) {
        List<ValidationScript> scripts = scriptRepository.findByEnvironmentAndNamespaceAndEnabled(env, ns, true);

        if (scripts.isEmpty()) {
            return new ValidationResult(true, "No validation scripts configured");
        }

        for (ValidationScript script : scripts) {
            log.debug("Running validation script '{}' for key '{}'", script.getScriptName(), configKey);
            ValidationResult result = scriptExecutor.execute(script.getScriptContent(), configKey, configValue);
            if (!result.isValid()) {
                log.info("Validation failed by script '{}': {}", script.getScriptName(), result.getMessage());
                return result;
            }
        }

        return new ValidationResult(true, "All validation scripts passed");
    }

    @Override
    public List<ValidationScriptDTO> listScripts(String env, String ns) {
        List<ValidationScript> scripts = scriptRepository.findByEnvironmentAndNamespace(env, ns);
        return scripts.stream().map(this::toDTO).toList();
    }

    @Override
    @Transactional
    public ValidationScriptDTO createScript(ValidationScriptRequest request) {
        ValidationScript script = new ValidationScript();
        script.setEnvironment(request.getEnvironment());
        script.setNamespace(request.getNamespace() != null ? request.getNamespace() : "default");
        script.setScriptName(request.getScriptName());
        script.setScriptContent(request.getScriptContent());
        script.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);

        ValidationScript saved = scriptRepository.save(script);
        log.info("Created validation script '{}' for env={}, ns={}", saved.getScriptName(), saved.getEnvironment(), saved.getNamespace());
        return toDTO(saved);
    }

    @Override
    @Transactional
    public ValidationScriptDTO updateScript(Long id, ValidationScriptRequest request) {
        ValidationScript script = scriptRepository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Validation script not found: " + id));

        script.setEnvironment(request.getEnvironment());
        script.setNamespace(request.getNamespace() != null ? request.getNamespace() : "default");
        script.setScriptName(request.getScriptName());
        script.setScriptContent(request.getScriptContent());
        if (request.getEnabled() != null) {
            script.setEnabled(request.getEnabled());
        }

        ValidationScript saved = scriptRepository.save(script);
        log.info("Updated validation script id={}", id);
        return toDTO(saved);
    }

    @Override
    @Transactional
    public void deleteScript(Long id) {
        if (!scriptRepository.existsById(id)) {
            throw new ConfigNotFoundException("Validation script not found: " + id);
        }
        scriptRepository.deleteById(id);
        log.info("Deleted validation script id={}", id);
    }

    @Override
    public ValidationResult testScript(ValidationTestRequest request) {
        return scriptExecutor.execute(request.getScriptContent(), request.getTestConfigKey(), request.getTestConfigValue());
    }

    private ValidationScriptDTO toDTO(ValidationScript entity) {
        ValidationScriptDTO dto = new ValidationScriptDTO();
        dto.setId(entity.getId());
        dto.setEnvironment(entity.getEnvironment());
        dto.setNamespace(entity.getNamespace());
        dto.setScriptName(entity.getScriptName());
        dto.setScriptContent(entity.getScriptContent());
        dto.setEnabled(entity.getEnabled());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
