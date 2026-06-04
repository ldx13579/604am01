package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {

    List<ConfigItem> findByEnvironmentAndNamespace(String environment, String namespace);

    boolean existsByConfigKeyAndEnvironmentAndNamespace(String configKey, String environment, String namespace);
}
