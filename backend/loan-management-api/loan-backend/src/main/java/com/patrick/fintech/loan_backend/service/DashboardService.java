
package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.DashboardStats;
import com.patrick.fintech.loan_backend.model.LoanStatus;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final BorrowerRepository borrowerRepository;

    public DashboardService(
            LoanRepository loanRepository,
            PaymentRepository paymentRepository,
            BorrowerRepository borrowerRepository) {

        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
        this.borrowerRepository = borrowerRepository;
    }

    public DashboardStats getStats(Long orgId) {

        // ============================================================
        // COUNTS
        // ============================================================

        long totalLoans =
                loanRepository.countByOrganization_Id(orgId);

        long activeLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.ACTIVE
                );

        long pendingLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.PENDING
                );

        long completedLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.PAID
                );

        long defaultedLoans =
                loanRepository.countByOrganization_IdAndStatus(
                        orgId,
                        LoanStatus.DEFAULTED
                );

        long overdueLoans =
                paymentRepository
                        .findByOrganization_IdAndPaidFalseAndDueDateBefore(
                                orgId,
                                LocalDate.now()
                        )
                        .size();

        long totalBorrowers =
                borrowerRepository
                        .findByOrganization_Id(orgId)
                        .size();

        // ============================================================
        // TOTAL AMOUNT LENT
        // ============================================================

        BigDecimal totalAmountLent =
                loanRepository
                        .findByOrganization_Id(orgId)
                        .stream()
                        .map(loan -> loan.getAmount() != null
                                ? loan.getAmount()
                                : BigDecimal.ZERO)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        // ============================================================
        // PAYMENTS COLLECTED
        // ============================================================

        BigDecimal paymentsCollected =
                paymentRepository
                        .findByLoan_Organization_Id(orgId)
                        .stream()
                        .filter(p ->
                                Boolean.TRUE.equals(p.getPaid())
                        )
                        .map(payment -> payment.getAmount() != null
                                ? payment.getAmount()
                                : BigDecimal.ZERO)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        // ============================================================
        // OUTSTANDING BALANCE
        // ============================================================

        BigDecimal outstandingBalance =
                loanRepository
                        .findByOrganization_Id(orgId)
                        .stream()
                        .map(loan -> loan.getOutstandingBalance() != null
                                ? loan.getOutstandingBalance()
                                : BigDecimal.ZERO)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        // ============================================================
        // COLLECTIONS THIS MONTH
        // ============================================================

        LocalDate firstDayOfMonth =
                LocalDate.now().withDayOfMonth(1);

        BigDecimal collectedThisMonth =
                paymentRepository
                        .findByLoan_Organization_Id(orgId)
                        .stream()
                        .filter(p ->
                                Boolean.TRUE.equals(p.getPaid())
                        )
                        .filter(p ->
                                p.getPaidDate() != null
                                        && !p.getPaidDate()
                                                .isBefore(firstDayOfMonth)
                                        && !p.getPaidDate()
                                                .isAfter(LocalDate.now())
                        )
                        .map(payment -> payment.getAmount() != null
                                ? payment.getAmount()
                                : BigDecimal.ZERO)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        // ============================================================
        // PORTFOLIO AT RISK
        //
        // PAR = overdue/defaulted outstanding balance
        //      / total outstanding balance * 100
        // ============================================================

        BigDecimal atRiskBalance =
                loanRepository
                        .findByOrganization_Id(orgId)
                        .stream()
                        .filter(loan ->
                                loan.getStatus() == LoanStatus.OVERDUE
                                        || loan.getStatus() == LoanStatus.DEFAULTED
                        )
                        .map(loan ->
                                loan.getOutstandingBalance() != null
                                        ? loan.getOutstandingBalance()
                                        : BigDecimal.ZERO
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        double portfolioAtRiskPct = 0.0;

        if (outstandingBalance.compareTo(BigDecimal.ZERO) > 0) {

            portfolioAtRiskPct =
                    atRiskBalance
                            .divide(
                                    outstandingBalance,
                                    6,
                                    java.math.RoundingMode.HALF_UP
                            )
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();
        }

        // ============================================================
        // DASHBOARD RESPONSE
        //
        // DashboardStats currently appears to use double monetary
        // fields, so convert only at the DTO boundary.
        // ============================================================

        return DashboardStats.builder()

                .totalLoans(totalLoans)

                .activeLoans(activeLoans)

                .pendingLoans(pendingLoans)

                .completedLoans(completedLoans)

                .defaultedLoans(defaultedLoans)

                .overdueLoans(overdueLoans)

                .totalBorrowers(totalBorrowers)

                .totalDisbursed(
                        totalAmountLent.doubleValue()
                )

                .totalCollected(
                        paymentsCollected.doubleValue()
                )

                .outstandingBalance(
                        outstandingBalance.doubleValue()
                )

                .collectedThisMonth(
                        collectedThisMonth.doubleValue()
                )

                .latePaymentsCount(overdueLoans)

                .portfolioAtRiskPct(
                        portfolioAtRiskPct
                )

                .build();
    }
}
