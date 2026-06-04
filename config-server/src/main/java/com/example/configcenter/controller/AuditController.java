package com.example.configcenter.controller;

import com.example.configcenter.model.dto.AuditLogDTO;
import com.example.configcenter.model.dto.AuditLogQuery;
import com.example.configcenter.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogDTO> queryAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditLogQuery query = new AuditLogQuery();
        query.setUsername(username);
        query.setAction(action);
        query.setResourceType(resourceType);
        query.setEnvironment(environment);
        query.setNamespace(namespace);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setPage(page);
        query.setSize(size);

        return auditService.query(query);
    }
}
