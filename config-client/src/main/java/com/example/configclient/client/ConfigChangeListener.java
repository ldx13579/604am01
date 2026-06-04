package com.example.configclient.client;

import java.util.Map;

@FunctionalInterface
public interface ConfigChangeListener {
    void onConfigChange(Map<String, String> configs, Long version);
}
