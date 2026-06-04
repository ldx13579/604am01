package com.example.configcenter.repository;

import com.example.configcenter.model.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findByMetricName(String metricName);
}
