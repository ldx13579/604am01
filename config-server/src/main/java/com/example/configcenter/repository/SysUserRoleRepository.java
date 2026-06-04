package com.example.configcenter.repository;

import com.example.configcenter.model.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);

    void deleteByUserIdAndId(Long userId, Long id);
}
