package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ValidationScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ValidationScriptRepository extends JpaRepository<ValidationScript, Long> {

    List<ValidationScript> findByEnvironmentAndNamespaceAndEnabled(String environment, String namespace, Boolean enabled);

    List<ValidationScript> findByEnvironmentAndNamespace(String environment, String namespace);

    Optional<ValidationScript> findById(Long id);
}
