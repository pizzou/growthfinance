package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class DashboardService {

    private final LoanRepository      loanRepository;
    private final PaymentRepository   paymentRepository;
    private final BorrowerRepository  borrowerRepository;

    public DashboardService(LoanRepository l, PaymentRepository p, BorrowerRepository b) {
        this.loanRepository = l; this.paymentRepository = p; this.borrowerRepository = b;
    }

    public DashboardStats getStats(Long orgId) {
        long totalLoans     = loanRepository.countByOrganization_Id(orgId);
        long activeLoans    = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.ACTIVE);
        long pendingLoans   = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.PENDING);
        long completedLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.PAID);
        long defaultedLoans = loanRepository.countByOrganization_IdAndStatus(orgId, LoanStatus.DEFAULTED);
        long overdueLoans   = paymentRepository
            .findByOrganization_IdAndPaidFalseAndDueDateBefore(orgId, LocalDate.now()).size();
        long totalBorrowers = borrowerRepository.findByOrganization_Id(orgId).size();

        double totalAmountLent = loanRepository.findByOrganization_Id(orgId).stream()
            .mapToDouble(l -> l.getAmount() != null ? l.getAmount() : 0.0).sum();

        double paymentsCollected = paymentRepository.findByLoan_Organization_Id(orgId).stream()
            .filter(p -> Boolean.TRUE.equals(p.getPaid()))
            .mapToDouble(p -> p.getAmount() != null ? p.getAmount() : 0.0).sum();

        return DashboardStats.builder()
            .totalLoans(totalLoans)
            .activeLoans(activeLoans)
            .pendingLoans(pendingLoans)
            .completedLoans(completedLoans)
            .defaultedLoans(defaultedLoans)
            .overdueLoans(overdueLoans)
            .totalBorrowers(totalBorrowers)
            .totalDisbursed(totalAmountLent)
            .totalCollected(paymentsCollected)
            .outstandingBalance(0.0)
            .collectedThisMonth(0.0)
            .latePaymentsCount(overdueLoans)
            .portfolioAtRiskPct(0.0)
            .build();
    }
}
