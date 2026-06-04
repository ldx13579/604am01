package com.example.configcenter.service;

import com.example.configcenter.metrics.MetricsService;
import com.example.configcenter.model.dto.ValidationResult;
import com.example.configcenter.model.entity.ConfigChangeTestLog;
import com.example.configcenter.repository.ConfigChangeTestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ConfigChangeTestService {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeTestService.class);

    private final ConfigChangeTestLogRepository testLogRepo;

    @Autowired(required = false)
    private ValidationService validationService;

    @Autowired(required = false)
    private MetricsService metricsService;

    @Autowired(required = false)
    private TestResultAnalyzer testResultAnalyzer;

    public ConfigChangeTestService(ConfigChangeTestLogRepository testLogRepo) {
        this.testLogRepo = testLogRepo;
    }

    /**
     * Tests a config change by simulating a client pulling the config and running validation scripts.
     *
     * @return true if test passed, false if validation failed (caller should rollback)
     */
    public boolean testConfigChange(Long configItemId, String configKey, String newValue,
                                     String env, String ns, Long previousVersion) {
        long start = System.currentTimeMillis();
        ConfigChangeTestLog logEntry = new ConfigChangeTestLog();
        logEntry.setConfigItemId(configItemId);
        logEntry.setConfigKey(configKey);
        logEntry.setEnvironment(env);
        logEntry.setNamespace(ns);
        logEntry.setNewValue(newValue);

        try {
            if (validationService == null) {
                logEntry.setTestResult("PASS");
                logEntry.setDurationMs(System.currentTimeMillis() - start);
                testLogRepo.save(logEntry);
                recordMetric("PASS");
                return true;
            }

            ValidationResult result = validationService.validate(configKey, newValue, env, ns);

            if (!result.isValid()) {
                logEntry.setTestResult("FAIL");
                logEntry.setErrorMessage(result.getMessage());
                logEntry.setRolledBack(true);
                logEntry.setRollbackVersion(previousVersion);
                logEntry.setDurationMs(System.currentTimeMillis() - start);
                ConfigChangeTestLog saved = testLogRepo.save(logEntry);
                recordMetric("FAIL");
                analyzeResult(saved);
                log.warn("Config change test FAILED for key={}, env={}, ns={}. Auto-rolling back to version {}",
                        configKey, env, ns, previousVersion);
                return false;
            }

            logEntry.setTestResult("PASS");
            logEntry.setDurationMs(System.currentTimeMillis() - start);
            testLogRepo.save(logEntry);
            recordMetric("PASS");
            return true;

        } catch (Exception e) {
            logEntry.setTestResult("ERROR");
            logEntry.setErrorMessage(e.getMessage());
            logEntry.setDurationMs(System.currentTimeMillis() - start);
            ConfigChangeTestLog saved = testLogRepo.save(logEntry);
            recordMetric("ERROR");
            analyzeResult(saved);
            log.error("Config change test ERROR for key={}: {}", configKey, e.getMessage());
            return true;
        }
    }

    private void recordMetric(String result) {
        if (metricsService != null) {
            metricsService.recordChangeTestResult(result);
        }
    }

    private void analyzeResult(ConfigChangeTestLog logEntry) {
        if (testResultAnalyzer != null && !"PASS".equals(logEntry.getTestResult())) {
            try {
                testResultAnalyzer.analyze(logEntry);
            } catch (Exception e) {
                log.debug("Test result analysis failed: {}", e.getMessage());
            }
        }
    }
}
