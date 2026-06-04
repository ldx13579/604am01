package com.example.configcenter.repository;

import com.example.configcenter.model.entity.ConfigPullRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ConfigPullRecordRepository extends JpaRepository<ConfigPullRecord, Long> {

    void deleteByPulledAtBefore(LocalDateTime threshold);
}
