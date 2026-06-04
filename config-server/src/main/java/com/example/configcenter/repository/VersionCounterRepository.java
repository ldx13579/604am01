package com.example.configcenter.repository;

import com.example.configcenter.model.entity.VersionCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VersionCounterRepository extends JpaRepository<VersionCounter, Long> {

    Optional<VersionCounter> findByEnvironmentAndNamespace(String environment, String namespace);
}
