package com.example.configclient.client;

@FunctionalInterface
public interface ConfigKeyChangeListener {
    void onKeyChange(String key, String oldValue, String newValue, Long version);
}
