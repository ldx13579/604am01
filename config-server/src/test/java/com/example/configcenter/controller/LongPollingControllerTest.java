package com.example.configcenter.controller;

import com.example.configcenter.model.dto.ConfigCreateRequest;
import com.example.configcenter.model.dto.ConfigItemDTO;
import com.example.configcenter.model.dto.PollingResponse;
import com.example.configcenter.model.dto.ConfigUpdateRequest;
import com.example.configcenter.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LongPollingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        ConfigCreateRequest request = new ConfigCreateRequest();
        request.setConfigKey("poll.test.key");
        request.setConfigValue("initial-value");
        request.setEnvironment("dev");
        request.setNamespace("default");
        configService.createConfig(request);
    }

    @Test
    void polling_shouldReturnImmediately_whenServerVersionGreater() throws Exception {
        // Client has version 0, server has version 1 (from setUp)
        mockMvc.perform(get("/api/polling")
                        .param("env", "dev")
                        .param("ns", "default")
                        .param("clientVersion", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChange").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.configs").isArray())
                .andExpect(jsonPath("$.configs[0].configKey").value("poll.test.key"));
    }

    @Test
    void polling_shouldWaitAndReturnOnChange() throws Exception {
        Long currentVersion = configService.getCurrentVersion("dev", "default");

        // Start async polling request
        MvcResult mvcResult = mockMvc.perform(get("/api/polling")
                        .param("env", "dev")
                        .param("ns", "default")
                        .param("clientVersion", String.valueOf(currentVersion)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Trigger a config change in another thread
        Thread.sleep(500);
        ConfigCreateRequest newConfig = new ConfigCreateRequest();
        newConfig.setConfigKey("poll.new.key");
        newConfig.setConfigValue("new-value");
        newConfig.setEnvironment("dev");
        newConfig.setNamespace("default");
        configService.createConfig(newConfig);

        // Wait for async result
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChange").value(true))
                .andExpect(jsonPath("$.version").value(currentVersion + 1));
    }

    @Test
    void version_shouldReturnCurrentVersion() throws Exception {
        mockMvc.perform(get("/api/version")
                        .param("env", "dev")
                        .param("ns", "default"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }
}
