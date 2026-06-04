package com.example.configcenter.repository;

import com.example.configcenter.model.entity.SecurityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityRuleRepository extends JpaRepository<SecurityRule, Long> {

    List<SecurityRule> findByEnabledTrue();

    List<SecurityRule> findByRuleTypeAndEnabledTrue(String ruleType);
}
