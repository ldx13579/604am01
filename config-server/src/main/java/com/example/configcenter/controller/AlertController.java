package com.example.configcenter.controller;

import com.example.configcenter.model.entity.AlertHistory;
import com.example.configcenter.model.entity.AlertRule;
import com.example.configcenter.service.AlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/rules")
    public List<AlertRule> listRules() {
        return alertService.listRules();
    }

    @PostMapping("/rules")
    public AlertRule createRule(@RequestBody AlertRule rule) {
        return alertService.createRule(rule);
    }

    @PutMapping("/rules/{id}")
    public AlertRule updateRule(@PathVariable Long id, @RequestBody AlertRule rule) {
        return alertService.updateRule(id, rule);
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
    }

    @GetMapping("/history")
    public List<AlertHistory> getHistory() {
        return alertService.getAlertHistory();
    }

    @PostMapping("/check")
    public void triggerCheck() {
        alertService.checkAlerts();
    }
}
