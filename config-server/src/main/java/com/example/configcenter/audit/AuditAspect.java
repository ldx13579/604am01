package com.example.configcenter.audit;

import com.example.configcenter.service.AuditService;
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

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
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

        if ("UPDATE".equalsIgnoreCase(action) || "DELETE".equalsIgnoreCase(action)) {
            oldValue = fetchOldState(resourceId);
        }

        Object result;
        try {
            result = joinPoint.proceed();

            String newValue = result != null ? result.toString() : null;
            auditService.record(username, action, resourceType, resourceId,
                    null, null, oldValue, newValue, ip, "SUCCESS", null);

            return result;
        } catch (Throwable ex) {
            auditService.record(username, action, resourceType, resourceId,
                    null, null, oldValue, null, ip, "FAILURE", ex.getMessage());
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
