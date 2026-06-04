package com.example.configcenter.service;

import com.example.configcenter.model.dto.AuditLogDTO;
import com.example.configcenter.model.dto.AuditLogQuery;
import org.springframework.data.domain.Page;

public interface AuditService {

    void record(String username, String action, String resourceType, String resourceId,
                String env, String ns, String oldValue, String newValue,
                String ip, String result, String errorMessage);

    Page<AuditLogDTO> query(AuditLogQuery query);
}
