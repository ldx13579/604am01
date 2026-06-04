package com.example.configcenter.security;

import com.example.configcenter.model.entity.SysUser;
import com.example.configcenter.model.entity.SysUserRole;
import com.example.configcenter.repository.SysUserRepository;
import com.example.configcenter.repository.SysUserRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;
    private final SysUserRoleRepository userRoleRepository;

    public CustomUserDetailsService(SysUserRepository userRepository,
                                    SysUserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SysUserRole> roles = userRoleRepository.findByUserId(user.getId());
        List<GrantedAuthority> authorities = new ArrayList<>();

        for (SysUserRole role : roles) {
            String authority = "ROLE_" + role.getRole() + "_" + role.getEnvironment() + "_" + role.getNamespace();
            authorities.add(new SimpleGrantedAuthority(authority));
        }

        return new User(user.getUsername(), user.getPasswordHash(), user.getEnabled(),
                true, true, true, authorities);
    }
}
