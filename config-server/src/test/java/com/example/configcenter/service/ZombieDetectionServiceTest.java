package com.example.configcenter.service;

import com.example.configcenter.config.TestRabbitMQConfig;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.entity.ConfigItem;
import com.example.configcenter.repository.ConfigItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
    "zombie.detection.threshold-days=30"
})
@Transactional
public class ZombieDetectionServiceTest {

    @Autowired
    private ZombieDetectionService zombieDetectionService;

    @Autowired
    private ConfigItemRepository configItemRepo;

    @BeforeEach
    void setup() {
        configItemRepo.deleteAll();
    }

    @Test
    void configNeverPulledShouldBeZombie() {
        ConfigItem item = createConfig("zombie.key", "dev");
        // lastPulledAt is null by default
        assertNull(item.getLastPulledAt());

        zombieDetectionService.detectZombies();

        ConfigItem updated = configItemRepo.findById(item.getId()).orElseThrow();
        assertTrue(updated.getZombie());
    }

    @Test
    void configPulledRecentlyShouldNotBeZombie() {
        ConfigItem item = createConfig("active.key", "dev");
        item.setLastPulledAt(LocalDateTime.now().minusDays(1));
        configItemRepo.save(item);

        zombieDetectionService.detectZombies();

        ConfigItem updated = configItemRepo.findById(item.getId()).orElseThrow();
        assertFalse(updated.getZombie());
    }

    @Test
    void zombieConfigPulledAgainShouldBeUnmarked() {
        ConfigItem item = createConfig("revived.key", "dev");
        item.setZombie(true);
        item.setLastPulledAt(LocalDateTime.now().minusDays(60));
        configItemRepo.save(item);

        // Simulate a pull
        zombieDetectionService.recordPull("dev", "default", "127.0.0.1");

        ConfigItem updated = configItemRepo.findById(item.getId()).orElseThrow();
        assertFalse(updated.getZombie());
        assertNotNull(updated.getLastPulledAt());
    }

    @Test
    void getZombieConfigsReturnsOnlyZombies() {
        ConfigItem zombie = createConfig("zombie.key", "dev");
        zombie.setZombie(true);
        configItemRepo.save(zombie);

        ConfigItem active = createConfig("active.key", "dev");
        active.setZombie(false);
        active.setLastPulledAt(LocalDateTime.now());
        configItemRepo.save(active);

        List<ConfigItemDTO> zombies = zombieDetectionService.getZombieConfigs("dev", "default");
        assertEquals(1, zombies.size());
        assertEquals("zombie.key", zombies.get(0).getConfigKey());
    }

    private ConfigItem createConfig(String key, String env) {
        ConfigItem item = new ConfigItem();
        item.setConfigKey(key);
        item.setConfigValue("value");
        item.setEnvironment(env);
        item.setNamespace("default");
        item.setVersion(1L);
        return configItemRepo.save(item);
    }
}
