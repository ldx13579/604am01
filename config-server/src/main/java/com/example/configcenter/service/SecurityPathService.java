package com.example.configcenter.service;

import com.example.configcenter.model.entity.SecurityPublicPath;
import com.example.configcenter.repository.SecurityPublicPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SecurityPathService {

    private final SecurityPublicPathRepository repository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private volatile List<String> cachedPaths = new CopyOnWriteArrayList<>();
    private final AtomicLong lastRefresh = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 30_000;

    public SecurityPathService(SecurityPublicPathRepository repository) {
        this.repository = repository;
    }

    public boolean isPublicPath(String requestPath) {
        refreshCacheIfNeeded();
        for (String pattern : cachedPaths) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    public List<SecurityPublicPath> listAll() {
        return repository.findAll();
    }

    public List<SecurityPublicPath> listEnabled() {
        return repository.findByEnabledTrue();
    }

    @Transactional
    public SecurityPublicPath create(String pathPattern, String description) {
        if (repository.existsByPathPattern(pathPattern)) {
            throw new RuntimeException("Path pattern already exists: " + pathPattern);
        }
        SecurityPublicPath path = new SecurityPublicPath();
        path.setPathPattern(pathPattern);
        path.setDescription(description);
        path.setEnabled(true);
        SecurityPublicPath saved = repository.save(path);
        refreshCache();
        return saved;
    }

    @Transactional
    public SecurityPublicPath update(Long id, String pathPattern, String description, Boolean enabled) {
        SecurityPublicPath path = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Public path not found: " + id));
        if (pathPattern != null) path.setPathPattern(pathPattern);
        if (description != null) path.setDescription(description);
        if (enabled != null) path.setEnabled(enabled);
        SecurityPublicPath saved = repository.save(path);
        refreshCache();
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
        refreshCache();
    }

    public void refreshCache() {
        List<SecurityPublicPath> paths = repository.findByEnabledTrue();
        cachedPaths = paths.stream().map(SecurityPublicPath::getPathPattern).toList();
        lastRefresh.set(System.currentTimeMillis());
    }

    private void refreshCacheIfNeeded() {
        if (System.currentTimeMillis() - lastRefresh.get() > CACHE_TTL_MS) {
            refreshCache();
        }
    }
}
