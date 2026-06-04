package com.example.configcenter.service;

import com.example.configcenter.model.dto.RoleAssignRequest;
import com.example.configcenter.model.dto.UserCreateRequest;
import com.example.configcenter.model.dto.UserDTO;
import com.example.configcenter.model.entity.SysUserRole;

import java.util.List;

public interface UserService {

    List<UserDTO> listUsers();

    UserDTO createUser(UserCreateRequest request);

    void deleteUser(Long id);

    SysUserRole assignRole(Long userId, RoleAssignRequest request);

    void removeRole(Long userId, Long roleId);

    UserDTO getUserByUsername(String username);
}
