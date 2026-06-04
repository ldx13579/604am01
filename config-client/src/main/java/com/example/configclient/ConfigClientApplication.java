package com.example.configclient;

import com.example.configclient.client.ConfigClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ConfigClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(ConfigClient configClient) {
        return args -> {
            configClient.addListener((configs, version) -> {
                System.out.println("=== Config Updated (version " + version + ") ===");
                configs.forEach((key, value) ->
                        System.out.println("  " + key + " = " + value));
            });
            configClient.start();
        };
    }
}
