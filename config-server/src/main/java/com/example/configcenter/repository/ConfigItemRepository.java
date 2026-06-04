package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ConfigItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ConfigItemRepository extends JpaRepository<ConfigItem, Long> {

    List<ConfigItem> findByEnvironmentAndNamespace(String environment, String namespace);

    boolean existsByConfigKeyAndEnvironmentAndNamespace(String configKey, String environment, String namespace);

    List<ConfigItem> findByEnvironmentAndNamespaceAndZombieTrue(String environment, String namespace);

    List<ConfigItem> findByLastPulledAtBeforeOrLastPulledAtIsNull(LocalDateTime threshold);

    @Modifying
    @Query("UPDATE ConfigItem c SET c.lastPulledAt = :now, c.zombie = false WHERE c.environment = :env AND c.namespace = :ns")
    int updateLastPulledAt(@Param("env") String environment, @Param("ns") String namespace, @Param("now") LocalDateTime now);
}
