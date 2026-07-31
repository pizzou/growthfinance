package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.PaymentHistoryResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.UpcomingInstallmentResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicPortalService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    public BorrowerDashboardResponse getDashboard(BorrowerDashboardRequest request) {

        String phoneHash =
                com.patrick.fintech.loan_backend.security.HmacIndexer
                        .index(request.getPhone());

        Loan loan = loanRepository
                .findByReferenceNumberAndBorrower_PhoneHash(
                        request.getReference(),
                        phoneHash)
                .orElseThrow(() ->
                        new RuntimeException("Application not found"));

        int daysUntilDue = 0;

        if (loan.getNextPaymentDate() != null) {
            daysUntilDue =
                    (int) ChronoUnit.DAYS.between(
                            LocalDate.now(),
                            loan.getNextPaymentDate());
        }

        //----------------------------------------------------
        // Repayment Progress
        //----------------------------------------------------

        double repaymentProgress = 0;

        if (loan.getTotalRepayable() != null
                && loan.getTotalRepayable() > 0
                && loan.getTotalPaid() != null) {

            repaymentProgress =
                    (loan.getTotalPaid()
                            / loan.getTotalRepayable()) * 100;

            if (repaymentProgress > 100) {
                repaymentProgress = 100;
            }
        }

        //----------------------------------------------------
        // Recent Payments
        //----------------------------------------------------

        List<PaymentHistoryResponse> recentPayments =
                paymentRepository
                        .findTop10ByLoanIdOrderByPaidDateDesc(loan.getId())
                        .stream()
                        .map(payment ->
                                PaymentHistoryResponse.builder()
                                        .paymentId(payment.getId())
                                        .paymentDate(payment.getPaidDate())
                                        .amount(payment.getAmountPaid())
                                        .method(payment.getPaymentMethod())
                                        .status(payment.getStatus().name())
                                        .build())
                        .toList();

        //----------------------------------------------------
        // Upcoming Installments
        //----------------------------------------------------
        // Sourced from the live payment ledger (the same table
        // PaymentService.recordPayment() updates on every real payment) —
        // not the one-time projected schedule generated at disbursement,
        // which never changes once a borrower starts paying flexible
        // amounts and would otherwise drift out of sync with reality.

        List<UpcomingInstallmentResponse> upcomingInstallments =
                paymentRepository
                        .findByLoanId(loan.getId())
                        .stream()
                        .filter(p -> !p.getPaid())
                        .sorted(java.util.Comparator.comparing(Payment::getDueDate))
                        .limit(6)
                        .map(payment ->
                                UpcomingInstallmentResponse.builder()
                                        .installmentNumber(payment.getInstallmentNumber())
                                        .dueDate(payment.getDueDate())
                                        .amount(payment.getAmount())
                                        .principal(payment.getPrincipalComponent())
                                        .interest(payment.getInterestComponent())
                                        .status(payment.getStatus() != null ? payment.getStatus().name() : "PENDING")
                                        .build())
                        .toList();

        //----------------------------------------------------
        // Payment Methods
        //----------------------------------------------------

        List<String> paymentMethods = List.of(
                "MTN Mobile Money",
                "Airtel Money",
                "Bank Transfer",
                "Visa / Mastercard"
        );

        //----------------------------------------------------
        // Loan Statistics
        //----------------------------------------------------

        List<Loan> borrowerLoans =
                loanRepository.findByBorrowerIdAndOrganizationId(
                        loan.getBorrower().getId(),
                        loan.getOrganization().getId());

        int activeLoans = 0;
        int overdueLoans = 0;
        int completedLoans = 0;

        for (Loan l : borrowerLoans) {

            if (l.getStatus().name().equals("ACTIVE")) {
                activeLoans++;
            }

            if (l.getStatus().name().equals("OVERDUE")) {
                overdueLoans++;
            }

            if (l.getStatus().name().equals("PAID") || l.getStatus().name().equals("CLOSED")) {
                completedLoans++;
            }
        }

        //----------------------------------------------------
        // Response
        //----------------------------------------------------

        return BorrowerDashboardResponse.builder()

                .loanId(loan.getId())
                .referenceNumber(loan.getReferenceNumber())
                .borrowerName(loan.getBorrower().getFullName())

                .status(loan.getStatus().name())
                .loanType(loan.getLoanType().name())

                .principal(loan.getAmount())
                .outstandingBalance(loan.getOutstandingBalance())
                .totalPaid(loan.getTotalPaid())
                .totalRepayable(loan.getTotalRepayable())

                .nextInstallmentAmount(
                        loan.getNextInstallmentAmount())

                .nextPaymentDate(
                        loan.getNextPaymentDate())

                .nextDueDate(
                        loan.getNextDueDate())

                .maturityDate(
                        loan.getMaturityDate())

                .interestRate(
                        loan.getInterestRate())

                .currency(
                        loan.getCurrency())

                .loanOfficer(
                        loan.getLoanOfficer() == null
                                ? null
                                : loan.getLoanOfficer().getFullName())

                .missedInstallments(
                        loan.getMissedInstallments())

                .daysOverdue(
                        loan.getDaysOverdue())

                .daysUntilDue(daysUntilDue)

                .repaymentProgress(repaymentProgress)

                .activeLoans(activeLoans)
                .overdueLoans(overdueLoans)
                .completedLoans(completedLoans)

                .recentPayments(recentPayments)

                .upcomingInstallments(upcomingInstallments)

                .availablePaymentMethods(paymentMethods)

                .build();
    }
}