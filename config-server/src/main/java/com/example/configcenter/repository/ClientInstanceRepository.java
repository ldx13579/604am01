package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ClientInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClientInstanceRepository extends JpaRepository<ClientInstance, Long> {

    Optional<ClientInstance> findByClientIpAndEnvironmentAndNamespace(String clientIp, String environment, String namespace);

    List<ClientInstance> findByEnvironmentAndNamespace(String environment, String namespace);

    List<ClientInstance> findByLastHeartbeatBefore(LocalDateTime threshold);
}
