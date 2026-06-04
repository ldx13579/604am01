package com.example.configcenter.service;

import com.example.configcenter.model.entity.SysUser;
import com.example.configcenter.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountLockoutService {

    @Value("${security.lockout.max-attempts:5}")
    private int maxAttempts;

    @Value("${security.lockout.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    private final SysUserRepository userRepository;

    public AccountLockoutService(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean isAccountLocked(SysUser user) {
        if (user.getLockedUntil() == null) {
            return false;
        }
        if (user.getLockedUntil().isAfter(LocalDateTime.now())) {
            return true;
        }
        unlockAccount(user);
        return false;
    }

    @Transactional
    public void recordFailedAttempt(SysUser user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
        }
        userRepository.save(user);
    }

    @Transactional
    public void resetFailedAttempts(SysUser user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Transactional
    public void unlockAccount(SysUser user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    @Transactional
    public void adminUnlock(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        unlockAccount(user);
    }

    public int getRemainingAttempts(SysUser user) {
        return Math.max(0, maxAttempts - user.getFailedLoginAttempts());
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Scheduled(fixedDelayString = "${security.lockout.cleanup-interval-ms:60000}")
    @Transactional
    public void autoUnlockExpiredAccounts() {
        List<SysUser> lockedUsers = userRepository.findByLockedUntilBefore(LocalDateTime.now());
        for (SysUser user : lockedUsers) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        if (!lockedUsers.isEmpty()) {
            userRepository.saveAll(lockedUsers);
        }
    }
}
