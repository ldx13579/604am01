package com.example.configcenter.repository;

import com.example.configcenter.model.entity.CleanupPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CleanupPolicyRepository extends JpaRepository<CleanupPolicy, Long> {

    List<CleanupPolicy> findByEnabledTrueOrderByPriorityDesc();
}
