package com.example.configcenter.repository;

import com.example.configcenter.model.entity.SecurityPublicPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecurityPublicPathRepository extends JpaRepository<SecurityPublicPath, Long> {

    List<SecurityPublicPath> findByEnabledTrue();

    boolean existsByPathPattern(String pathPattern);
}
