package com.example.configcenter.model.dto;

import com.example.configcenter.model.entity.SysUserRole;

import java.util.List;

public class LoginResponse {

    private String token;
    private String username;
    private String displayName;
    private List<SysUserRole> roles;

    public LoginResponse() {}

    public LoginResponse(String token, String username, String displayName, List<SysUserRole> roles) {
        this.token = token;
        this.username = username;
        this.displayName = displayName;
        this.roles = roles;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<SysUserRole> getRoles() { return roles; }
    public void setRoles(List<SysUserRole> roles) { this.roles = roles; }
}
