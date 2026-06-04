package com.example.configcenter.audit;

import com.example.configcenter.model.entity.AuditLog;
import com.example.configcenter.repository.AuditLogRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(com.example.configcenter.audit.Auditable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        String action = auditable.action();
        String resourceType = auditable.resourceType();
        String username = AuditContext.getUsername();
        String ip = AuditContext.getIp();

        String resourceId = extractResourceId(joinPoint);
        String oldValue = null;

        if (action.contains("UPDATE") || action.contains("DELETE") || action.contains("ROLLBACK")) {
            oldValue = fetchOldState(resourceId);
        }

        long startTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
            long durationMs = System.currentTimeMillis() - startTime;

            String newValue = result != null ? result.toString() : null;

            AuditLog log = new AuditLog();
            log.setUsername(username != null ? username : "anonymous");
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            log.setIpAddress(ip);
            log.setUserAgent(AuditContext.getUserAgent());
            log.setRequestMethod(AuditContext.getRequestMethod());
            log.setRequestUri(AuditContext.getRequestUri());
            log.setSessionId(AuditContext.getSessionId());
            log.setDurationMs(durationMs);
            log.setResult("SUCCESS");
            auditLogRepository.save(log);

            return result;
        } catch (Throwable ex) {
            long durationMs = System.currentTimeMillis() - startTime;

            AuditLog log = new AuditLog();
            log.setUsername(username != null ? username : "anonymous");
            log.setAction(action);
            log.setResourceType(resourceType);
            log.setResourceId(resourceId);
            log.setOldValue(oldValue);
            log.setIpAddress(ip);
            log.setUserAgent(AuditContext.getUserAgent());
            log.setRequestMethod(AuditContext.getRequestMethod());
            log.setRequestUri(AuditContext.getRequestUri());
            log.setSessionId(AuditContext.getSessionId());
            log.setDurationMs(durationMs);
            log.setResult("FAILURE");
            log.setErrorMessage(ex.getMessage());
            auditLogRepository.save(log);

            throw ex;
        }
    }

    private String extractResourceId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if ("id".equals(parameters[i].getName())) {
                return args[i] != null ? args[i].toString() : null;
            }
        }

        if (args.length > 0 && args[0] != null) {
            return args[0].toString();
        }

        return null;
    }

    private String fetchOldState(String resourceId) {
        if (resourceId == null) {
            return null;
        }
        return "id=" + resourceId;
    }
}
