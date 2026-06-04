package com.example.configclient;

import com.example.configclient.client.ConfigClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class ConfigClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(ConfigClient configClient) {
        return args -> {
            AtomicBoolean keyListenersRegistered = new AtomicBoolean(false);

            configClient.addListener((configs, version) -> {
                System.out.println("=== Config Updated (version " + version + ") ===");
                configs.forEach((key, value) ->
                        System.out.println("  " + key + " = " + value));

                if (keyListenersRegistered.compareAndSet(false, true)) {
                    if (configClient.getConfig("db.url") != null) {
                        configClient.addKeyListener("db.url", (k, oldVal, newVal, v) -> {
                            System.out.println("=== Database config changed, refreshing connection pool ===");
                            System.out.println("  Old: " + oldVal);
                            System.out.println("  New: " + newVal);
                        });
                    }
                    if (configClient.getConfig("db.pool.size") != null) {
                        configClient.addKeyListener("db.pool.size", (k, oldVal, newVal, v) -> {
                            System.out.println("=== Pool size changed, adjusting connection pool ===");
                            System.out.println("  Old size: " + oldVal + " -> New size: " + newVal);
                        });
                    }
                }
            });

            configClient.start();
        };
    }
}
