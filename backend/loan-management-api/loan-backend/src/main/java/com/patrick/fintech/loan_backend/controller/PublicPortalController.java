package com.patrick.fintech.loan_backend.controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.service.PublicPortalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicPortalController {

    private final PublicPortalService publicPortalService;

    @PostMapping("/dashboard")
    public BorrowerDashboardResponse dashboard(
            @RequestBody BorrowerDashboardRequest request) {

        return publicPortalService.getDashboard(request);
    }

}