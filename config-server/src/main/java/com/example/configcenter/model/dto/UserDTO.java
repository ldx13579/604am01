package com.example.configcenter.model.dto;

import com.example.configcenter.model.entity.SysUserRole;

import java.time.LocalDateTime;
import java.util.List;

public class UserDTO {

    private Long id;
    private String username;
    private String displayName;
    private Boolean enabled;
    private List<SysUserRole> roles;
    private LocalDateTime createdAt;

    public UserDTO() {}

    public UserDTO(Long id, String username, String displayName, Boolean enabled,
                   List<SysUserRole> roles, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.enabled = enabled;
        this.roles = roles;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public List<SysUserRole> getRoles() { return roles; }
    public void setRoles(List<SysUserRole> roles) { this.roles = roles; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
