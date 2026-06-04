package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ClientReconnectLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientReconnectLogRepository extends JpaRepository<ClientReconnectLog, Long> {

    List<ClientReconnectLog> findByClientId(String clientId);

    List<ClientReconnectLog> findByEventType(String eventType);

    long countByEventType(String eventType);
}
