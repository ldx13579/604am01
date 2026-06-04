package com.example.configcenter.service;

import com.example.configcenter.model.dto.ClientInstanceDTO;
import com.example.configcenter.model.dto.GrayscaleRuleRequest;
import com.example.configcenter.model.entity.ClientInstance;
import com.example.configcenter.model.entity.GrayscaleRule;
import com.example.configcenter.repository.ClientInstanceRepository;
import com.example.configcenter.repository.GrayscaleRuleRepository;
import com.example.configcenter.exception.ConfigNotFoundException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrayscaleService {

    private final GrayscaleRuleRepository grayscaleRuleRepo;
    private final ClientInstanceRepository clientInstanceRepo;

    public GrayscaleService(GrayscaleRuleRepository grayscaleRuleRepo,
                            ClientInstanceRepository clientInstanceRepo) {
        this.grayscaleRuleRepo = grayscaleRuleRepo;
        this.clientInstanceRepo = clientInstanceRepo;
    }

    public List<GrayscaleRule> listRules(String environment, String namespace) {
        return grayscaleRuleRepo.findByEnvironmentAndNamespace(environment, namespace);
    }

    @Transactional
    public GrayscaleRule createRule(GrayscaleRuleRequest request) {
        GrayscaleRule rule = new GrayscaleRule();
        rule.setEnvironment(request.getEnvironment());
        rule.setNamespace(request.getNamespace());
        rule.setRuleName(request.getRuleName());
        rule.setTargetVersion(request.getTargetVersion());
        rule.setIpList(request.getIpList());
        rule.setStatus("ACTIVE");
        return grayscaleRuleRepo.save(rule);
    }

    @Transactional
    public GrayscaleRule updateRule(Long id, GrayscaleRuleRequest request) {
        GrayscaleRule rule = grayscaleRuleRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Grayscale rule not found: " + id));
        rule.setRuleName(request.getRuleName());
        rule.setTargetVersion(request.getTargetVersion());
        rule.setIpList(request.getIpList());
        return grayscaleRuleRepo.save(rule);
    }

    @Transactional
    public GrayscaleRule fullRelease(Long id) {
        GrayscaleRule rule = grayscaleRuleRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Grayscale rule not found: " + id));
        rule.setStatus("FULL_RELEASE");
        return grayscaleRuleRepo.save(rule);
    }

    @Transactional
    public GrayscaleRule cancelRule(Long id) {
        GrayscaleRule rule = grayscaleRuleRepo.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException("Grayscale rule not found: " + id));
        rule.setStatus("CANCELLED");
        return grayscaleRuleRepo.save(rule);
    }

    public boolean isClientInGrayscale(String clientIp, String environment, String namespace) {
        List<GrayscaleRule> activeRules = grayscaleRuleRepo
                .findByEnvironmentAndNamespaceAndStatus(environment, namespace, "ACTIVE");
        for (GrayscaleRule rule : activeRules) {
            Set<String> ips = parseIpList(rule.getIpList());
            if (ips.contains(clientIp)) {
                return true;
            }
        }
        return false;
    }

    public Long getGrayscaleTargetVersion(String clientIp, String environment, String namespace) {
        List<GrayscaleRule> activeRules = grayscaleRuleRepo
                .findByEnvironmentAndNamespaceAndStatus(environment, namespace, "ACTIVE");
        for (GrayscaleRule rule : activeRules) {
            Set<String> ips = parseIpList(rule.getIpList());
            if (ips.contains(clientIp)) {
                return rule.getTargetVersion();
            }
        }
        return null;
    }

    @Transactional
    public void registerHeartbeat(String clientIp, String environment, String namespace, Long currentVersion) {
        ClientInstance instance = clientInstanceRepo
                .findByClientIpAndEnvironmentAndNamespace(clientIp, environment, namespace)
                .orElseGet(() -> {
                    ClientInstance ci = new ClientInstance();
                    ci.setClientIp(clientIp);
                    ci.setEnvironment(environment);
                    ci.setNamespace(namespace);
                    return ci;
                });
        instance.setCurrentVersion(currentVersion);
        instance.setStatus("ONLINE");
        instance.setLastHeartbeat(LocalDateTime.now());
        clientInstanceRepo.save(instance);
    }

    public List<ClientInstanceDTO> listClients(String environment, String namespace) {
        List<ClientInstance> clients = clientInstanceRepo.findByEnvironmentAndNamespace(environment, namespace);
        List<GrayscaleRule> activeRules = grayscaleRuleRepo
                .findByEnvironmentAndNamespaceAndStatus(environment, namespace, "ACTIVE");

        Set<String> grayscaleIps = activeRules.stream()
                .flatMap(r -> parseIpList(r.getIpList()).stream())
                .collect(Collectors.toSet());

        return clients.stream().map(c -> {
            ClientInstanceDTO dto = new ClientInstanceDTO();
            dto.setId(c.getId());
            dto.setClientIp(c.getClientIp());
            dto.setEnvironment(c.getEnvironment());
            dto.setNamespace(c.getNamespace());
            dto.setCurrentVersion(c.getCurrentVersion());
            dto.setStatus(c.getStatus());
            dto.setLastHeartbeat(c.getLastHeartbeat());
            dto.setInGrayscale(grayscaleIps.contains(c.getClientIp()));
            return dto;
        }).toList();
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void markOfflineClients() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<ClientInstance> staleClients = clientInstanceRepo.findByLastHeartbeatBefore(threshold);
        for (ClientInstance client : staleClients) {
            if ("ONLINE".equals(client.getStatus())) {
                client.setStatus("OFFLINE");
                clientInstanceRepo.save(client);
            }
        }
    }

    private Set<String> parseIpList(String ipList) {
        if (ipList == null || ipList.isBlank()) return Set.of();
        return Arrays.stream(ipList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
