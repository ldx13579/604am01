package com.example.configcenter.repository;

import com.example.configcenter.model.entity.EncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, Long> {

    Optional<EncryptionKey> findFirstByEnvironmentAndNamespaceOrderByKeyVersionDesc(String environment, String namespace);

    List<EncryptionKey> findByEnvironmentAndNamespace(String environment, String namespace);
}
