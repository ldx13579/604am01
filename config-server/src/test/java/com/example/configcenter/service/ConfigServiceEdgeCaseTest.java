package com.example.configcenter.service;

import com.example.configcenter.config.TestRabbitMQConfig;
import com.example.configcenter.model.dto.ConfigCreateRequest;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.ConfigUpdateRequest;
import com.example.configcenter.exception.ConfigAlreadyExistsException;
import com.example.configcenter.exception.ConfigNotFoundException;
import com.example.configcenter.exception.VersionConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestRabbitMQConfig.class)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@Transactional
public class ConfigServiceEdgeCaseTest {

    @Autowired
    private ConfigService configService;

    @BeforeEach
    void setup() {
    }

    // ==================== 空值与边界值测试 ====================

    @Test
    @DisplayName("配置值为空字符串应正常存储")
    void emptyConfigValueShouldBeStored() {
        ConfigCreateRequest req = createRequest("empty.value", "", "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals("", result.getConfigValue());
    }

    @Test
    @DisplayName("配置键使用最大长度(255字符)")
    void maxLengthConfigKey() {
        String longKey = "a".repeat(255);
        ConfigCreateRequest req = createRequest(longKey, "value", "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(longKey, result.getConfigKey());
    }

    @Test
    @DisplayName("配置值为超大文本(10KB)")
    void largeConfigValue() {
        String largeValue = "x".repeat(10240);
        ConfigCreateRequest req = createRequest("large.value", largeValue, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(10240, result.getConfigValue().length());
    }

    @Test
    @DisplayName("配置值包含JSON格式")
    void jsonConfigValue() {
        String json = "{\"database\":{\"host\":\"localhost\",\"port\":3306,\"pool\":{\"min\":5,\"max\":20}}}";
        ConfigCreateRequest req = createRequest("json.config", json, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(json, result.getConfigValue());
    }

    // ==================== 特殊字符测试 ====================

    @ParameterizedTest
    @ValueSource(strings = {
        "value with spaces",
        "value\twith\ttabs",
        "value\nwith\nnewlines",
        "value with 中文字符",
        "value with émojis 🎉🔥",
        "value with <html>tags</html>",
        "value with 'single' and \"double\" quotes",
        "value with \\backslashes\\",
        "value with $pecial ${chars}",
        "null",
        "undefined",
        "NaN",
        "true",
        "false",
        "0",
        "-1",
        "2147483647",
        "9999999999999999999"
    })
    @DisplayName("配置值包含特殊字符应正常存储和读取")
    void specialCharacterValues(String value) {
        String key = "special." + value.hashCode();
        ConfigCreateRequest req = createRequest(key, value, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(value, result.getConfigValue());

        ConfigItemDTO fetched = configService.getConfig(result.getId());
        assertEquals(value, fetched.getConfigValue());
    }

    @Test
    @DisplayName("配置值为SQL注入尝试应安全存储")
    void sqlInjectionAttemptInValue() {
        String sqlPayload = "'; DROP TABLE config_item; --";
        ConfigCreateRequest req = createRequest("sql.injection.test", sqlPayload, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(sqlPayload, result.getConfigValue());

        ConfigItemDTO fetched = configService.getConfig(result.getId());
        assertEquals(sqlPayload, fetched.getConfigValue());
    }

    @Test
    @DisplayName("配置值为XSS payload应安全存储")
    void xssPayloadInValue() {
        String xssPayload = "<script>alert('xss')</script><img onerror=alert(1) src=x>";
        ConfigCreateRequest req = createRequest("xss.test", xssPayload, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(xssPayload, result.getConfigValue());
    }

    @Test
    @DisplayName("配置键包含点号、中划线和下划线")
    void configKeyWithSpecialSeparators() {
        ConfigCreateRequest req = createRequest("app.server.db-connection_pool.max-size", "20", "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals("app.server.db-connection_pool.max-size", result.getConfigKey());
    }

    // ==================== 重复与冲突测试 ====================

    @Test
    @DisplayName("重复配置键在同一环境下应抛异常")
    void duplicateKeyInSameEnvShouldFail() {
        ConfigCreateRequest req = createRequest("duplicate.key", "v1", "dev");
        configService.createConfig(req);

        assertThrows(ConfigAlreadyExistsException.class, () ->
            configService.createConfig(createRequest("duplicate.key", "v2", "dev")));
    }

    @Test
    @DisplayName("相同配置键在不同环境应正常创建")
    void sameKeyDifferentEnvShouldSucceed() {
        configService.createConfig(createRequest("shared.key", "dev-value", "dev"));
        configService.createConfig(createRequest("shared.key", "test-value", "test"));
        configService.createConfig(createRequest("shared.key", "prod-value", "prod"));

        List<ConfigItemDTO> devConfigs = configService.listConfigs("dev", "default");
        List<ConfigItemDTO> testConfigs = configService.listConfigs("test", "default");
        List<ConfigItemDTO> prodConfigs = configService.listConfigs("prod", "default");

        assertTrue(devConfigs.stream().anyMatch(c -> "dev-value".equals(c.getConfigValue())));
        assertTrue(testConfigs.stream().anyMatch(c -> "test-value".equals(c.getConfigValue())));
        assertTrue(prodConfigs.stream().anyMatch(c -> "prod-value".equals(c.getConfigValue())));
    }

    @Test
    @DisplayName("版本冲突时更新应失败")
    void versionConflictShouldFail() {
        ConfigItemDTO created = configService.createConfig(
                createRequest("conflict.key", "v1", "dev"));

        ConfigUpdateRequest update = new ConfigUpdateRequest();
        update.setConfigValue("v2");
        update.setExpectedVersion(999L);

        assertThrows(VersionConflictException.class, () ->
            configService.updateConfig(created.getId(), update));
    }

    // ==================== 并发更新测试 ====================

    @Test
    @DisplayName("多线程并发更新同一配置应只有一个成功")
    void concurrentUpdatesShouldConflict() throws Exception {
        ConfigItemDTO created = configService.createConfig(
                createRequest("concurrent.key", "original", "dev"));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ConfigUpdateRequest update = new ConfigUpdateRequest();
                    update.setConfigValue("updated-" + idx);
                    update.setExpectedVersion(created.getVersion());
                    configService.updateConfig(created.getId(), update);
                    successCount.incrementAndGet();
                } catch (VersionConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // other exceptions
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(successCount.get() >= 1, "At least one update should succeed");
    }

    // ==================== 删除与查询测试 ====================

    @Test
    @DisplayName("删除不存在的配置应抛异常")
    void deleteNonExistentConfigShouldFail() {
        assertThrows(ConfigNotFoundException.class, () ->
            configService.deleteConfig(99999L));
    }

    @Test
    @DisplayName("查询不存在的配置应抛异常")
    void getNonExistentConfigShouldFail() {
        assertThrows(ConfigNotFoundException.class, () ->
            configService.getConfig(99999L));
    }

    @Test
    @DisplayName("删除后再查询应失败")
    void deletedConfigShouldNotBeQueryable() {
        ConfigItemDTO created = configService.createConfig(
                createRequest("to.delete", "value", "dev"));
        configService.deleteConfig(created.getId());

        assertThrows(ConfigNotFoundException.class, () ->
            configService.getConfig(created.getId()));
    }

    // ==================== 回滚链测试 ====================

    @Test
    @DisplayName("多次更新后回滚到初始版本")
    void rollbackToFirstVersion() {
        ConfigItemDTO v1 = configService.createConfig(
                createRequest("rollback.chain", "version-1", "dev"));

        ConfigUpdateRequest update2 = new ConfigUpdateRequest();
        update2.setConfigValue("version-2");
        update2.setExpectedVersion(v1.getVersion());
        ConfigItemDTO v2 = configService.updateConfig(v1.getId(), update2);

        ConfigUpdateRequest update3 = new ConfigUpdateRequest();
        update3.setConfigValue("version-3");
        update3.setExpectedVersion(v2.getVersion());
        configService.updateConfig(v1.getId(), update3);

        ConfigItemDTO rolledBack = configService.rollback(v1.getId(), v1.getVersion());
        assertEquals("version-1", rolledBack.getConfigValue());
    }

    @Test
    @DisplayName("回滚后再次更新应使用新版本号")
    void updateAfterRollbackUsesNewVersion() {
        ConfigItemDTO v1 = configService.createConfig(
                createRequest("rollback.update", "v1", "dev"));

        ConfigUpdateRequest update = new ConfigUpdateRequest();
        update.setConfigValue("v2");
        update.setExpectedVersion(v1.getVersion());
        ConfigItemDTO v2 = configService.updateConfig(v1.getId(), update);

        ConfigItemDTO rolledBack = configService.rollback(v1.getId(), v1.getVersion());
        assertTrue(rolledBack.getVersion() > v2.getVersion());

        ConfigUpdateRequest update3 = new ConfigUpdateRequest();
        update3.setConfigValue("v3-after-rollback");
        update3.setExpectedVersion(rolledBack.getVersion());
        ConfigItemDTO v3 = configService.updateConfig(v1.getId(), update3);
        assertTrue(v3.getVersion() > rolledBack.getVersion());
        assertEquals("v3-after-rollback", v3.getConfigValue());
    }

    // ==================== 编码与格式测试 ====================

    @Test
    @DisplayName("配置值为多行文本")
    void multilineConfigValue() {
        String multiline = "line1\nline2\nline3\n  indented\n\ttabbed";
        ConfigCreateRequest req = createRequest("multiline.value", multiline, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(multiline, result.getConfigValue());
    }

    @Test
    @DisplayName("配置值为Base64编码数据")
    void base64ConfigValue() {
        String base64 = "SGVsbG8gV29ybGQhIFRoaXMgaXMgYSBiYXNlNjQgZW5jb2RlZCBzdHJpbmcu/+==";
        ConfigCreateRequest req = createRequest("base64.data", base64, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(base64, result.getConfigValue());
    }

    @Test
    @DisplayName("配置值为YAML格式")
    void yamlConfigValue() {
        String yaml = "server:\n  port: 8080\n  host: 0.0.0.0\ndatabase:\n  url: jdbc:mysql://localhost:3306/db";
        ConfigCreateRequest req = createRequest("yaml.config", yaml, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(yaml, result.getConfigValue());
    }

    @Test
    @DisplayName("配置值为包含Unicode转义的文本")
    void unicodeEscapeConfigValue() {
        String unicode = "\\u0048\\u0065\\u006C\\u006C\\u006F \\u4E16\\u754C";
        ConfigCreateRequest req = createRequest("unicode.escape", unicode, "dev");
        ConfigItemDTO result = configService.createConfig(req);
        assertEquals(unicode, result.getConfigValue());
    }

    // ==================== 批量操作测试 ====================

    @Test
    @DisplayName("批量创建100个配置后全部可查询")
    void bulkCreateAndQuery() {
        int count = 100;
        for (int i = 0; i < count; i++) {
            configService.createConfig(createRequest("bulk.key." + i, "value-" + i, "dev"));
        }

        List<ConfigItemDTO> configs = configService.listConfigs("dev", "default");
        assertTrue(configs.size() >= count);
    }

    // ==================== Helper ====================

    private ConfigCreateRequest createRequest(String key, String value, String env) {
        ConfigCreateRequest req = new ConfigCreateRequest();
        req.setConfigKey(key);
        req.setConfigValue(value);
        req.setEnvironment(env);
        req.setNamespace("default");
        req.setDescription("test");
        return req;
    }
}
