package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Long> {

    List<ConfigVersion> findByConfigItemIdOrderByVersionDesc(Long configItemId);

    Optional<ConfigVersion> findByConfigItemIdAndVersion(Long configItemId, Long version);
}
