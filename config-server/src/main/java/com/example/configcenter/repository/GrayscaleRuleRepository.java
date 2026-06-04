package com.example.configcenter.repository;

import com.example.configcenter.model.entity.GrayscaleRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrayscaleRuleRepository extends JpaRepository<GrayscaleRule, Long> {

    List<GrayscaleRule> findByEnvironmentAndNamespace(String environment, String namespace);

    List<GrayscaleRule> findByEnvironmentAndNamespaceAndStatus(String environment, String namespace, String status);
}
