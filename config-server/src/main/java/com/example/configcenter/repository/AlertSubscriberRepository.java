package com.example.configcenter.repository;

import com.example.configcenter.model.entity.AlertSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertSubscriberRepository extends JpaRepository<AlertSubscriber, Long> {

    List<AlertSubscriber> findByRoleGroupAndEnabledTrue(String roleGroup);

    List<AlertSubscriber> findByEnabledTrue();
}
