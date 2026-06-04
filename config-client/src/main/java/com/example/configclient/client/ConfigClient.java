package com.example.configclient.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ConfigClient {

    @Value("${config.server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${config.client.env:dev}")
    private String environment;

    @Value("${config.client.namespace:default}")
    private String namespace;

    private volatile Long localVersion = 0L;
    private final ConcurrentHashMap<String, String> configCache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile boolean running = false;
    private Thread pollingThread;

    public void start() {
        if (running) return;
        running = true;
        pollingThread = new Thread(this::pollingLoop, "config-polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
        System.out.println("Config client started, polling " + serverUrl
                + " env=" + environment + " ns=" + namespace);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
    }

    public void addListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public String getConfig(String key) {
        return configCache.get(key);
    }

    public String getConfig(String key, String defaultValue) {
        return configCache.getOrDefault(key, defaultValue);
    }

    public Map<String, String> getAllConfigs() {
        return Collections.unmodifiableMap(configCache);
    }

    public Long getLocalVersion() {
        return localVersion;
    }

    private void pollingLoop() {
        while (running) {
            try {
                String url = String.format("%s/api/polling?env=%s&ns=%s&clientVersion=%d",
                        serverUrl, environment, namespace, localVersion);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);

                if (response != null && Boolean.TRUE.equals(response.get("hasChange"))) {
                    Long newVersion = ((Number) response.get("version")).longValue();
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> configs = (List<Map<String, Object>>) response.get("configs");

                    configCache.clear();
                    if (configs != null) {
                        for (Map<String, Object> config : configs) {
                            configCache.put(
                                    (String) config.get("configKey"),
                                    (String) config.get("configValue")
                            );
                        }
                    }
                    localVersion = newVersion;
                    notifyListeners();
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Polling error: " + e.getMessage() + ", retrying in 3s...");
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
                }
            }
        }
    }

    private void notifyListeners() {
        Map<String, String> snapshot = Map.copyOf(configCache);
        for (ConfigChangeListener listener : listeners) {
            try {
                listener.onConfigChange(snapshot, localVersion);
            } catch (Exception e) {
                System.err.println("Listener error: " + e.getMessage());
            }
        }
    }
}
