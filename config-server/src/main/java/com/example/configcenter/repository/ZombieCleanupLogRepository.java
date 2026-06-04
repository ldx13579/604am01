package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ZombieCleanupLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZombieCleanupLogRepository extends JpaRepository<ZombieCleanupLog, Long> {

    List<ZombieCleanupLog> findTop50ByOrderByCreatedAtDesc();

    List<ZombieCleanupLog> findByEnvironmentAndNamespaceOrderByCreatedAtDesc(String environment, String namespace);
}
