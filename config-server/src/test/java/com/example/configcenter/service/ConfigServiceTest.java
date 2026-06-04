package com.example.configcenter.service;

import com.example.configcenter.config.TestRabbitMQConfig;
import com.example.configcenter.model.dto.ConfigCreateRequest;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.ConfigUpdateRequest;
import com.example.configcenter.model.entity.ConfigVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestRabbitMQConfig.class)
@Transactional
class ConfigServiceTest {

    @Autowired
    private ConfigService configService;

    private ConfigItemDTO createdConfig;

    @BeforeEach
    void setUp() {
        ConfigCreateRequest request = new ConfigCreateRequest();
        request.setConfigKey("app.name");
        request.setConfigValue("test-app");
        request.setEnvironment("dev");
        request.setNamespace("default");
        request.setDescription("Application name");
        createdConfig = configService.createConfig(request);
    }

    @Test
    void createConfig_shouldStoreWithVersion() {
        assertNotNull(createdConfig.getId());
        assertEquals("app.name", createdConfig.getConfigKey());
        assertEquals("test-app", createdConfig.getConfigValue());
        assertEquals(1L, createdConfig.getVersion());
    }

    @Test
    void updateConfig_shouldIncrementVersion() {
        ConfigUpdateRequest updateReq = new ConfigUpdateRequest();
        updateReq.setConfigValue("updated-app");
        updateReq.setExpectedVersion(1L);

        ConfigItemDTO updated = configService.updateConfig(createdConfig.getId(), updateReq);

        assertEquals("updated-app", updated.getConfigValue());
        assertEquals(2L, updated.getVersion());
    }

    @Test
    void deleteConfig_shouldRemoveItem() {
        configService.deleteConfig(createdConfig.getId());

        List<ConfigItemDTO> configs = configService.listConfigs("dev", "default");
        assertTrue(configs.isEmpty());
    }

    @Test
    void versionHistory_shouldTrackAllChanges() {
        ConfigUpdateRequest update1 = new ConfigUpdateRequest();
        update1.setConfigValue("v2");
        update1.setExpectedVersion(1L);
        configService.updateConfig(createdConfig.getId(), update1);

        ConfigUpdateRequest update2 = new ConfigUpdateRequest();
        update2.setConfigValue("v3");
        update2.setExpectedVersion(2L);
        configService.updateConfig(createdConfig.getId(), update2);

        List<ConfigVersion> history = configService.getVersionHistory(createdConfig.getId());

        assertEquals(3, history.size());
        assertEquals("CREATE", history.get(2).getOperation());
        assertEquals("UPDATE", history.get(1).getOperation());
        assertEquals("UPDATE", history.get(0).getOperation());
    }

    @Test
    void rollback_shouldRestoreValueAndCreateNewVersion() {
        ConfigUpdateRequest update1 = new ConfigUpdateRequest();
        update1.setConfigValue("v2-value");
        update1.setExpectedVersion(1L);
        configService.updateConfig(createdConfig.getId(), update1);

        ConfigUpdateRequest update2 = new ConfigUpdateRequest();
        update2.setConfigValue("v3-value");
        update2.setExpectedVersion(2L);
        configService.updateConfig(createdConfig.getId(), update2);

        // Rollback to version 1 (original value "test-app")
        ConfigItemDTO rolledBack = configService.rollback(createdConfig.getId(), 1L);

        assertEquals("test-app", rolledBack.getConfigValue());
        assertEquals(4L, rolledBack.getVersion()); // new version, not 1

        List<ConfigVersion> history = configService.getVersionHistory(createdConfig.getId());
        assertEquals("ROLLBACK", history.get(0).getOperation());
    }

    @Test
    void environmentIsolation_shouldNotAffectOtherEnv() {
        ConfigCreateRequest prodReq = new ConfigCreateRequest();
        prodReq.setConfigKey("app.name");
        prodReq.setConfigValue("prod-app");
        prodReq.setEnvironment("prod");
        prodReq.setNamespace("default");
        configService.createConfig(prodReq);

        Long devVersion = configService.getCurrentVersion("dev", "default");
        Long prodVersion = configService.getCurrentVersion("prod", "default");

        // Update only dev
        ConfigUpdateRequest devUpdate = new ConfigUpdateRequest();
        devUpdate.setConfigValue("dev-updated");
        devUpdate.setExpectedVersion(createdConfig.getVersion());
        configService.updateConfig(createdConfig.getId(), devUpdate);

        Long newDevVersion = configService.getCurrentVersion("dev", "default");
        Long newProdVersion = configService.getCurrentVersion("prod", "default");

        assertEquals(devVersion + 1, newDevVersion);
        assertEquals(prodVersion, newProdVersion);
    }

    @Test
    void getCurrentVersion_shouldIncrementOnEachChange() {
        Long v1 = configService.getCurrentVersion("dev", "default");

        ConfigUpdateRequest update = new ConfigUpdateRequest();
        update.setConfigValue("new-value");
        update.setExpectedVersion(createdConfig.getVersion());
        configService.updateConfig(createdConfig.getId(), update);

        Long v2 = configService.getCurrentVersion("dev", "default");
        assertEquals(v1 + 1, v2);
    }
}
