package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardRequest;
import com.patrick.fintech.loan_backend.dto.publicportal.BorrowerDashboardResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.PaymentHistoryResponse;
import com.patrick.fintech.loan_backend.dto.publicportal.UpcomingInstallmentResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.LoanRepository;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.security.HmacIndexer;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicPortalService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Public borrower dashboard.
     *
     * IMPORTANT:
     * This method is transactional because Loan contains
     * Hibernate lazy relationships such as Borrower and LoanOfficer.
     */
    @Transactional(readOnly = true)
    public BorrowerDashboardResponse getDashboard(
            BorrowerDashboardRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dashboard request is required");
        }

        if (request.getReference() == null
                || request.getReference().isBlank()) {

            throw new IllegalArgumentException(
                    "Reference number is required");
        }

        if (request.getPhone() == null
                || request.getPhone().isBlank()) {

            throw new IllegalArgumentException(
                    "Phone number is required");
        }

        //------------------------------------------------------------
        // HASH PHONE
        //------------------------------------------------------------

        String phoneHash =
                HmacIndexer.index(
                        request.getPhone().trim());

        //------------------------------------------------------------
        // FIND LOAN
        //
        // Repository query should fetch Borrower and LoanOfficer.
        //------------------------------------------------------------

        Loan loan =
                loanRepository
                        .findPublicDashboardLoan(
                                request.getReference().trim(),
                                phoneHash)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Application not found"));

        //------------------------------------------------------------
        // BASIC DATES
        //------------------------------------------------------------

        LocalDate today =
                LocalDate.now();

        int daysUntilDue = 0;

        if (loan.getNextPaymentDate() != null) {

            daysUntilDue =
                    (int) ChronoUnit.DAYS.between(
                            today,
                            loan.getNextPaymentDate());
        }

        //------------------------------------------------------------
        // REPAYMENT PROGRESS
        //------------------------------------------------------------

        double repaymentProgress = 0.0;

        Double totalRepayable =
                loan.getTotalRepayable();

        Double totalPaid =
                loan.getTotalPaid();

        if (totalRepayable != null
                && totalRepayable > 0
                && totalPaid != null) {

            repaymentProgress =
                    (totalPaid / totalRepayable) * 100.0;

            repaymentProgress =
                    Math.max(
                            0.0,
                            Math.min(
                                    repaymentProgress,
                                    100.0));
        }

        //------------------------------------------------------------
        // RECENT PAYMENTS
        //------------------------------------------------------------

        List<PaymentHistoryResponse> recentPayments =
                paymentRepository
                        .findTop10ByLoanIdOrderByPaidDateDesc(
                                loan.getId())
                        .stream()
                        .map(this::toPaymentHistoryResponse)
                        .toList();

        //------------------------------------------------------------
        // UPCOMING INSTALLMENTS
        //------------------------------------------------------------

        List<UpcomingInstallmentResponse> upcomingInstallments =
                paymentRepository
                        .findByLoanId(loan.getId())
                        .stream()
                        .filter(payment ->
                                Boolean.FALSE.equals(
                                        payment.getPaid()))
                        .sorted(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder())))
                        .limit(6)
                        .map(this::toUpcomingInstallmentResponse)
                        .toList();

        //------------------------------------------------------------
        // BORROWER
        //
        // Borrower is already fetched by repository query.
        //------------------------------------------------------------

        Long borrowerId =
                loan.getBorrower().getId();

        //------------------------------------------------------------
        // ORGANIZATION
        //
        // Organization is required for multi-tenant loan statistics.
        //------------------------------------------------------------

        Long organizationId =
                loan.getOrganization().getId();

        //------------------------------------------------------------
        // ALL BORROWER LOANS
        //------------------------------------------------------------

        List<Loan> borrowerLoans =
                loanRepository
                        .findByBorrowerIdAndOrganizationId(
                                borrowerId,
                                organizationId);

        //------------------------------------------------------------
        // LOAN STATISTICS
        //------------------------------------------------------------

        int activeLoans = 0;
        int overdueLoans = 0;
        int completedLoans = 0;

        for (Loan borrowerLoan : borrowerLoans) {

            if (borrowerLoan.getStatus() == null) {
                continue;
            }

            switch (borrowerLoan.getStatus().name()) {

                case "ACTIVE" -> activeLoans++;

                case "OVERDUE" -> overdueLoans++;

                case "PAID", "CLOSED" -> completedLoans++;

                default -> {
                    // Other statuses are ignored.
                }
            }
        }

        //------------------------------------------------------------
        // PAYMENT METHODS
        //------------------------------------------------------------

        List<String> paymentMethods =
                List.of(
                        "MTN Mobile Money",
                        "Airtel Money",
                        "Bank Transfer",
                        "Visa / Mastercard"
                );

        //------------------------------------------------------------
        // BORROWER NAME
        //------------------------------------------------------------

        String borrowerName = null;

        if (loan.getBorrower() != null) {

            borrowerName =
                    loan.getBorrower().getFullName();
        }

        //------------------------------------------------------------
        // LOAN OFFICER
        //------------------------------------------------------------

        String loanOfficer = null;

        if (loan.getLoanOfficer() != null) {

            loanOfficer =
                    loan.getLoanOfficer().getFullName();
        }

        //------------------------------------------------------------
        // RESPONSE
        //------------------------------------------------------------

        return BorrowerDashboardResponse.builder()

                .loanId(
                        loan.getId())

                .referenceNumber(
                        loan.getReferenceNumber())

                .borrowerName(
                        borrowerName)

                .status(
                        loan.getStatus() == null
                                ? null
                                : loan.getStatus().name())

                .loanType(
                        loan.getLoanType() == null
                                ? null
                                : loan.getLoanType().name())

                .principal(
                        loan.getAmount())

                .outstandingBalance(
                        loan.getOutstandingBalance())

                .totalPaid(
                        loan.getTotalPaid())

                .totalRepayable(
                        loan.getTotalRepayable())

                .nextInstallmentAmount(
                        loan.getNextInstallmentAmount())

                .nextPaymentDate(
                        loan.getNextPaymentDate())

                .nextDueDate(
                        loan.getNextDueDate())

                .maturityDate(
                        loan.getMaturityDate())

                .missedInstallments(
                        loan.getMissedInstallments())

                .daysOverdue(
                        loan.getDaysOverdue())

                .interestRate(
                        loan.getInterestRate())

                .currency(
                        loan.getCurrency())

                .loanOfficer(
                        loanOfficer)

                .activeLoans(
                        activeLoans)

                .overdueLoans(
                        overdueLoans)

                .completedLoans(
                        completedLoans)

                .daysUntilDue(
                        daysUntilDue)

                .repaymentProgress(
                        repaymentProgress)

                .recentPayments(
                        recentPayments)

                .upcomingInstallments(
                        upcomingInstallments)

                .availablePaymentMethods(
                        paymentMethods)

                .build();
    }

    //------------------------------------------------------------
    // PAYMENT HISTORY MAPPER
    //------------------------------------------------------------

    private PaymentHistoryResponse toPaymentHistoryResponse(
            Payment payment) {

        return PaymentHistoryResponse.builder()

                .paymentId(
                        payment.getId())

                .paymentDate(
                        payment.getPaidDate())

                .amount(
                        payment.getAmountPaid())

                .method(
                        payment.getPaymentMethod())

                .status(
                        payment.getStatus() == null
                                ? "UNKNOWN"
                                : payment.getStatus().name())

                .build();
    }

    //------------------------------------------------------------
    // UPCOMING INSTALLMENT MAPPER
    //------------------------------------------------------------

    private UpcomingInstallmentResponse
    toUpcomingInstallmentResponse(
            Payment payment) {

        return UpcomingInstallmentResponse.builder()

                .installmentNumber(
                        payment.getInstallmentNumber())

                .dueDate(
                        payment.getDueDate())

                .amount(
                        payment.getAmount())

                .principal(
                        payment.getPrincipalComponent())

                .interest(
                        payment.getInterestComponent())

                .status(
                        payment.getStatus() == null
                                ? "PENDING"
                                : payment.getStatus().name())

                .build();
    }
}