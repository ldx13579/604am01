package com.example.configcenter.service;

import com.example.configcenter.model.dto.*;

import java.util.List;

public interface ValidationService {

    ValidationResult validate(String configKey, String configValue, String env, String ns);

    List<ValidationScriptDTO> listScripts(String env, String ns);

    ValidationScriptDTO createScript(ValidationScriptRequest request);

    ValidationScriptDTO updateScript(Long id, ValidationScriptRequest request);

    void deleteScript(Long id);

    ValidationResult testScript(ValidationTestRequest request);
}
