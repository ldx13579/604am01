package com.example.configcenter.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Component
public class PermissionChecker {

    private static final List<String> ROLE_HIERARCHY = Arrays.asList("ADMIN", "DEVELOPER", "VIEWER");

    public boolean hasPermission(Authentication auth, String env, String ns, String requiredRole) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        int requiredLevel = ROLE_HIERARCHY.indexOf(requiredRole);
        if (requiredLevel < 0) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        for (GrantedAuthority authority : authorities) {
            String authorityStr = authority.getAuthority();
            if (!authorityStr.startsWith("ROLE_")) {
                continue;
            }

            String[] parts = authorityStr.substring(5).split("_", 3);
            if (parts.length != 3) {
                continue;
            }

            String userRole = parts[0];
            String userEnv = parts[1];
            String userNs = parts[2];

            int userLevel = ROLE_HIERARCHY.indexOf(userRole);
            if (userLevel < 0) {
                continue;
            }

            boolean envMatch = "*".equals(userEnv) || env == null || userEnv.equals(env);
            boolean nsMatch = "*".equals(userNs) || ns == null || userNs.equals(ns);
            boolean roleMatch = userLevel <= requiredLevel;

            if (envMatch && nsMatch && roleMatch) {
                return true;
            }
        }

        return false;
    }
}
