package com.example.configcenter.repository;

import com.example.configcenter.model.entity.AlertEscalationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEscalationPolicyRepository extends JpaRepository<AlertEscalationPolicy, Long> {

    List<AlertEscalationPolicy> findBySeverityAndEnabledTrueOrderByEscalationLevelAsc(String severity);

    List<AlertEscalationPolicy> findByEnabledTrueOrderBySeverityAscEscalationLevelAsc();
}
