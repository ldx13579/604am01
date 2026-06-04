package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ConfigChangeTestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigChangeTestLogRepository extends JpaRepository<ConfigChangeTestLog, Long> {

    List<ConfigChangeTestLog> findByConfigItemIdOrderByCreatedAtDesc(Long configItemId);

    List<ConfigChangeTestLog> findTop20ByTestResultOrderByCreatedAtDesc(String testResult);
}
