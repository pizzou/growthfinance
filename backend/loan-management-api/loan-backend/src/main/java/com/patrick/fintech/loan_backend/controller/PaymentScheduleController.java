package com.patrick.fintech.loan_backend.controller;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.patrick.fintech.loan_backend.dto.publicportal.PaymentScheduleResponse;
import com.patrick.fintech.loan_backend.service.PaymentScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/payment-schedule")
public class PaymentScheduleController {

    private final PaymentScheduleService service;

    @GetMapping("/{loanId}")

    public List<PaymentScheduleResponse> getSchedule(

            @PathVariable Long loanId){

        return service.getSchedule(loanId);

    }

}
