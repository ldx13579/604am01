package com.example.configcenter.controller;

import com.example.configcenter.model.dto.RoleAssignRequest;
import com.example.configcenter.model.dto.UserCreateRequest;
import com.example.configcenter.model.dto.UserDTO;
import com.example.configcenter.model.entity.SysUserRole;
import com.example.configcenter.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public List<UserDTO> listUsers() {
        return userService.listUsers();
    }

    @PostMapping
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserDTO created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<SysUserRole> assignRole(@PathVariable Long id,
                                                  @Valid @RequestBody RoleAssignRequest request) {
        SysUserRole role = userService.assignRole(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(role);
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("@permissionChecker.hasPermission(authentication, '*', '*', 'ADMIN')")
    public ResponseEntity<Void> removeRole(@PathVariable Long id, @PathVariable Long roleId) {
        userService.removeRole(id, roleId);
        return ResponseEntity.noContent().build();
    }
}
