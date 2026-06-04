package com.example.configclient.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;

@Component
public class ConfigClient {

    @Value("${config.server.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${config.client.env:dev}")
    private String environment;

    @Value("${config.client.namespace:default}")
    private String namespace;

    @Value("${config.client.max-retries:10}")
    private int maxRetries;

    @Value("${config.client.base-backoff-ms:1000}")
    private long baseBackoffMs;

    @Value("${config.client.max-backoff-ms:60000}")
    private long maxBackoffMs;

    @Value("${config.client.reconnect-interval-ms:30000}")
    private long reconnectIntervalMs;

    private volatile Long localVersion = 0L;
    private final ConcurrentHashMap<String, String> configCache = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile boolean running = false;
    private volatile boolean shutdown = false;
    private Thread pollingThread;
    private ScheduledExecutorService reconnectScheduler;

    public void start() {
        if (running) return;
        shutdown = false;
        running = true;
        pollingThread = new Thread(this::pollingLoop, "config-polling");
        pollingThread.setDaemon(true);
        pollingThread.start();
        System.out.println("Config client started, polling " + serverUrl
                + " env=" + environment + " ns=" + namespace);
    }

    @PreDestroy
    public void stop() {
        shutdown = true;
        running = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdownNow();
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

    public boolean isRunning() {
        return running;
    }

    private void pollingLoop() {
        int consecutiveFailures = 0;

        while (running) {
            try {
                String url = String.format("%s/api/polling?env=%s&ns=%s&clientVersion=%d",
                        serverUrl, environment, namespace, localVersion);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);

                consecutiveFailures = 0;

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
                if (!running) break;

                consecutiveFailures++;
                if (consecutiveFailures >= maxRetries) {
                    System.err.println("Polling failed " + maxRetries
                            + " times consecutively, entering reconnect mode.");
                    running = false;
                    scheduleReconnect();
                    break;
                }

                long backoff = Math.min(baseBackoffMs * (1L << (consecutiveFailures - 1)), maxBackoffMs);
                System.err.println("Polling error (attempt " + consecutiveFailures + "/" + maxRetries
                        + "): " + e.getMessage() + ", retrying in " + backoff + "ms...");
                try { Thread.sleep(backoff); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private void scheduleReconnect() {
        if (shutdown) return;

        if (reconnectScheduler == null || reconnectScheduler.isShutdown()) {
            reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "config-reconnect");
                t.setDaemon(true);
                return t;
            });
        }

        reconnectScheduler.scheduleAtFixedRate(() -> {
            if (shutdown || running) return;

            System.out.println("Attempting to reconnect to config server...");
            try {
                String url = String.format("%s/api/version?env=%s&ns=%s",
                        serverUrl, environment, namespace);
                restTemplate.getForObject(url, Long.class);

                System.out.println("Config server is reachable, restarting polling...");
                reconnectScheduler.shutdownNow();
                start();
            } catch (Exception e) {
                System.err.println("Reconnect probe failed: " + e.getMessage()
                        + ", next attempt in " + reconnectIntervalMs + "ms");
            }
        }, reconnectIntervalMs, reconnectIntervalMs, TimeUnit.MILLISECONDS);
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
