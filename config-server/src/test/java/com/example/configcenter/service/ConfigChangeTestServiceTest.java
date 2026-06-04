package com.example.configcenter.service;

import com.example.configcenter.config.TestRabbitMQConfig;
import com.example.configcenter.model.entity.ConfigChangeTestLog;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.model.entity.ValidationScript;
import com.example.configcenter.repository.ConfigChangeTestLogRepository;
import com.example.configcenter.repository.ConfigItemRepository;
import com.example.configcenter.repository.ValidationScriptRepository;
import com.example.configcenter.repository.VersionCounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@Transactional
public class ConfigChangeTestServiceTest {

    @Autowired
    private ConfigChangeTestService configChangeTestService;

    @Autowired
    private ConfigChangeTestLogRepository testLogRepo;

    @Autowired
    private ConfigItemRepository configItemRepo;

    @Autowired
    private VersionCounterRepository versionCounterRepo;

    @Autowired(required = false)
    private ValidationScriptRepository validationScriptRepo;

    @BeforeEach
    void setup() {
        testLogRepo.deleteAll();
        configItemRepo.deleteAll();
    }

    @Test
    void testPassesWhenNoValidationScripts() {
        ConfigItem item = createConfig("test.key", "test-value", "dev");

        boolean passed = configChangeTestService.testConfigChange(
                item.getId(), "test.key", "new-value", "dev", "default", 1L);

        assertTrue(passed);

        List<ConfigChangeTestLog> logs = testLogRepo.findByConfigItemIdOrderByCreatedAtDesc(item.getId());
        assertEquals(1, logs.size());
        assertEquals("PASS", logs.get(0).getTestResult());
        assertFalse(logs.get(0).getRolledBack());
    }

    @Test
    void testFailsWhenValidationRejects() {
        if (validationScriptRepo == null) return;

        ConfigItem item = createConfig("port.number", "8080", "dev");

        ValidationScript script = new ValidationScript();
        script.setEnvironment("dev");
        script.setNamespace("default");
        script.setScriptName("port-validation");
        script.setScriptContent("if (isNaN(parseInt(value))) { throw 'Port must be a number'; } true;");
        script.setEnabled(true);
        validationScriptRepo.save(script);

        boolean passed = configChangeTestService.testConfigChange(
                item.getId(), "port.number", "not-a-number", "dev", "default", 1L);

        assertFalse(passed);

        List<ConfigChangeTestLog> logs = testLogRepo.findByConfigItemIdOrderByCreatedAtDesc(item.getId());
        assertEquals(1, logs.size());
        assertEquals("FAIL", logs.get(0).getTestResult());
        assertTrue(logs.get(0).getRolledBack());
    }

    @Test
    void testLogRecordsDuration() {
        ConfigItem item = createConfig("duration.test", "value", "dev");

        configChangeTestService.testConfigChange(
                item.getId(), "duration.test", "new-value", "dev", "default", 1L);

        List<ConfigChangeTestLog> logs = testLogRepo.findByConfigItemIdOrderByCreatedAtDesc(item.getId());
        assertNotNull(logs.get(0).getDurationMs());
        assertTrue(logs.get(0).getDurationMs() >= 0);
    }

    private ConfigItem createConfig(String key, String value, String env) {
        ConfigItem item = new ConfigItem();
        item.setConfigKey(key);
        item.setConfigValue(value);
        item.setEnvironment(env);
        item.setNamespace("default");
        item.setVersion(1L);
        return configItemRepo.save(item);
    }
}
