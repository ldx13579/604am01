package com.example.configcenter.service;

import com.example.configcenter.model.dto.AuditLogDTO;
import com.example.configcenter.model.dto.AuditLogQuery;
import com.example.configcenter.model.entity.AuditLog;
import com.example.configcenter.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public void record(String username, String action, String resourceType, String resourceId,
                       String env, String ns, String oldValue, String newValue,
                       String ip, String result, String errorMessage) {
        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setEnvironment(env);
        log.setNamespace(ns);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(ip);
        log.setResult(result);
        log.setErrorMessage(errorMessage);
        auditLogRepository.save(log);
    }

    @Override
    public Page<AuditLogDTO> query(AuditLogQuery query) {
        Specification<AuditLog> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getUsername() != null && !query.getUsername().isEmpty()) {
                predicates.add(cb.equal(root.get("username"), query.getUsername()));
            }
            if (query.getAction() != null && !query.getAction().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), query.getAction()));
            }
            if (query.getResourceType() != null && !query.getResourceType().isEmpty()) {
                predicates.add(cb.equal(root.get("resourceType"), query.getResourceType()));
            }
            if (query.getEnvironment() != null && !query.getEnvironment().isEmpty()) {
                predicates.add(cb.equal(root.get("environment"), query.getEnvironment()));
            }
            if (query.getNamespace() != null && !query.getNamespace().isEmpty()) {
                predicates.add(cb.equal(root.get("namespace"), query.getNamespace()));
            }
            if (query.getStartTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.getStartTime()));
            }
            if (query.getEndTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.getEndTime()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(query.getPage(), query.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLog> page = auditLogRepository.findAll(spec, pageRequest);

        return page.map(this::toDTO);
    }

    private AuditLogDTO toDTO(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(log.getId());
        dto.setUsername(log.getUsername());
        dto.setAction(log.getAction());
        dto.setResourceType(log.getResourceType());
        dto.setResourceId(log.getResourceId());
        dto.setEnvironment(log.getEnvironment());
        dto.setNamespace(log.getNamespace());
        dto.setOldValue(log.getOldValue());
        dto.setNewValue(log.getNewValue());
        dto.setIpAddress(log.getIpAddress());
        dto.setResult(log.getResult());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }
}
