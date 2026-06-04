package com.example.configcenter.repository;

import com.example.configcenter.model.entity.AlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {

    List<AlertHistory> findTop50ByOrderByCreatedAtDesc();

    List<AlertHistory> findByAlertRuleIdOrderByCreatedAtDesc(Long alertRuleId);
}
