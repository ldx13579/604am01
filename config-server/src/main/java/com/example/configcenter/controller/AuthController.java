package com.example.configcenter.controller;

import com.example.configcenter.audit.AuditContext;
import com.example.configcenter.model.dto.LoginRequest;
import com.example.configcenter.model.dto.LoginResponse;
import com.example.configcenter.model.dto.UserDTO;
import com.example.configcenter.model.entity.SysUser;
import com.example.configcenter.model.entity.SysUserRole;
import com.example.configcenter.repository.SysUserRepository;
import com.example.configcenter.repository.SysUserRoleRepository;
import com.example.configcenter.security.JwtTokenProvider;
import com.example.configcenter.service.AuditService;
import com.example.configcenter.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider,
                          UserService userService,
                          SysUserRepository userRepository,
                          SysUserRoleRepository userRoleRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        SysUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String clientIp = getClientIp(httpRequest);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userRepository.save(user);

        auditService.record(request.getUsername(), "LOGIN", "SESSION", null,
                null, null, null, null, clientIp, "SUCCESS", null);

        String token = jwtTokenProvider.generateToken(request.getUsername());
        List<SysUserRole> roles = userRoleRepository.findByUserId(user.getId());

        if (Boolean.TRUE.equals(user.getForcePasswordChange())) {
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                    "roles", roles,
                    "forcePasswordChange", true
            ));
        }

        LoginResponse response = new LoginResponse(token, user.getUsername(), user.getDisplayName(), roles);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        UserDTO user = userService.getUserByUsername(userDetails.getUsername());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String newToken = jwtTokenProvider.generateToken(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        SysUser user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Current password is incorrect"));
        }

        if (request.newPassword == null || request.newPassword.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("message", "New password must be at least 8 characters"));
        }

        if (!isPasswordComplex(request.newPassword)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Password must contain uppercase, lowercase, digit, and special character"));
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        auditService.record(userDetails.getUsername(), "CHANGE_PASSWORD", "USER",
                user.getId().toString(), null, null, null, null,
                AuditContext.getIp(), "SUCCESS", null);

        String newToken = jwtTokenProvider.generateToken(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully", "token", newToken));
    }

    private boolean isPasswordComplex(String password) {
        boolean hasUpper = false, hasLower = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        return hasUpper && hasLower && hasDigit && hasSpecial;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    public static class ChangePasswordRequest {
        @NotBlank
        public String oldPassword;
        @NotBlank
        public String newPassword;
    }
}
