package com.example.configcenter.controller;

import com.example.configcenter.model.entity.AlertEscalationPolicy;
import com.example.configcenter.model.entity.AlertSubscriber;
import com.example.configcenter.service.AlertEscalationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/escalation")
public class AlertEscalationController {

    private final AlertEscalationService escalationService;

    public AlertEscalationController(AlertEscalationService escalationService) {
        this.escalationService = escalationService;
    }

    @GetMapping("/policies")
    public List<AlertEscalationPolicy> listPolicies() {
        return escalationService.listPolicies();
    }

    @PostMapping("/policies")
    public AlertEscalationPolicy createPolicy(@RequestBody AlertEscalationPolicy policy) {
        return escalationService.createPolicy(policy);
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        escalationService.deletePolicy(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscribers")
    public List<AlertSubscriber> listSubscribers() {
        return escalationService.listSubscribers();
    }

    @PostMapping("/subscribers")
    public AlertSubscriber createSubscriber(@RequestBody AlertSubscriber subscriber) {
        return escalationService.createSubscriber(subscriber);
    }

    @PutMapping("/subscribers/{id}")
    public AlertSubscriber updateSubscriber(@PathVariable Long id,
                                            @RequestBody AlertSubscriber subscriber) {
        return escalationService.updateSubscriber(id, subscriber);
    }

    @DeleteMapping("/subscribers/{id}")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable Long id) {
        escalationService.deleteSubscriber(id);
        return ResponseEntity.ok().build();
    }
}
