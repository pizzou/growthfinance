package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.service.DashboardService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserUtil currentUserUtil;

    public DashboardController(DashboardService dashboardService, CurrentUserUtil currentUserUtil) {
        this.dashboardService = dashboardService; this.currentUserUtil = currentUserUtil;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        return ResponseEntity.ok(dashboardService.getStats(currentUserUtil.getCurrentOrganizationId()));
    }
}