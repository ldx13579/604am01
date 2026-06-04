package com.example.configcenter.service;

import com.example.configcenter.model.dto.RoleAssignRequest;
import com.example.configcenter.model.dto.UserCreateRequest;
import com.example.configcenter.model.dto.UserDTO;
import com.example.configcenter.model.entity.SysUser;
import com.example.configcenter.model.entity.SysUserRole;
import com.example.configcenter.repository.SysUserRepository;
import com.example.configcenter.repository.SysUserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserRepository userRepository,
                           SysUserRoleRepository userRoleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserDTO> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEnabled(true);

        SysUser saved = userRepository.save(user);
        return toUserDTO(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public SysUserRole assignRole(Long userId, RoleAssignRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found: " + userId);
        }

        SysUserRole role = new SysUserRole();
        role.setUserId(userId);
        role.setRole(request.getRole());
        role.setEnvironment(request.getEnvironment());
        role.setNamespace(request.getNamespace());

        return userRoleRepository.save(role);
    }

    @Override
    @Transactional
    public void removeRole(Long userId, Long roleId) {
        userRoleRepository.deleteByUserIdAndId(userId, roleId);
    }

    @Override
    public UserDTO getUserByUsername(String username) {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return toUserDTO(user);
    }

    private UserDTO toUserDTO(SysUser user) {
        List<SysUserRole> roles = userRoleRepository.findByUserId(user.getId());
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEnabled(),
                roles,
                user.getCreatedAt()
        );
    }
}
