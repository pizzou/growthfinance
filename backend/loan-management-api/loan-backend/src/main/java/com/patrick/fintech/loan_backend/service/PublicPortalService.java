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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicPortalService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;

    // ================================================================
    // BORROWER DASHBOARD
    // ================================================================

    public BorrowerDashboardResponse getDashboard(
            BorrowerDashboardRequest request) {

        // ============================================================
        // FIND LOAN
        // ============================================================

        String phoneHash =
                HmacIndexer.index(
                        request.getPhone()
                );

        Loan loan =
                loanRepository
                        .findByReferenceNumberAndBorrower_PhoneHash(
                                request.getReference(),
                                phoneHash
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Application not found"
                                )
                        );

        // ============================================================
        // DAYS UNTIL NEXT PAYMENT
        // ============================================================

        int daysUntilDue = 0;

        LocalDate nextPaymentDate =
                loan.getNextPaymentDate();

        if (nextPaymentDate != null) {

            daysUntilDue =
                    (int) ChronoUnit.DAYS.between(
                            LocalDate.now(),
                            nextPaymentDate
                    );
        }

        // ============================================================
        // REPAYMENT PROGRESS
        // ============================================================

        double repaymentProgress = 0.0;

        BigDecimal totalRepayable =
                loan.getTotalRepayable();

        BigDecimal totalPaid =
                loan.getTotalPaid();

        if (totalRepayable != null
                && totalPaid != null
                && totalRepayable.compareTo(
                        BigDecimal.ZERO
                ) > 0) {

            repaymentProgress =
                    totalPaid
                            .divide(
                                    totalRepayable,
                                    6,
                                    RoundingMode.HALF_UP
                            )
                            .doubleValue()
                            * 100.0;

            if (repaymentProgress > 100.0) {
                repaymentProgress = 100.0;
            }

            if (repaymentProgress < 0.0) {
                repaymentProgress = 0.0;
            }
        }

        // ============================================================
        // RECENT PAYMENTS
        // ============================================================

        List<Payment> paymentHistory =
                paymentRepository
                        .findTop10ByLoanIdOrderByPaidDateDesc(
                                loan.getId()
                        );

        List<PaymentHistoryResponse> recentPayments =
                new ArrayList<>();

        for (Payment payment : paymentHistory) {

            PaymentHistoryResponse paymentResponse =
                    PaymentHistoryResponse
                            .builder()
                            .paymentId(
                                    payment.getId()
                            )
                            .paymentDate(
                                    payment.getPaidDate()
                            )
                            .amount(
                                    payment.getAmountPaid()
                            )
                            .method(
                                    payment.getPaymentMethod()
                            )
                            .status(
                                    payment.getStatus() != null
                                            ? payment.getStatus().name()
                                            : "PENDING"
                            )
                            .build();

            recentPayments.add(
                    paymentResponse
            );
        }

        // ============================================================
        // UPCOMING INSTALLMENTS
        // ============================================================

        List<Payment> allPayments =
                paymentRepository
                        .findByLoanId(
                                loan.getId()
                        );

        List<Payment> unpaidPayments =
                new ArrayList<>();

        for (Payment payment : allPayments) {

            if (!Boolean.TRUE.equals(
                    payment.getPaid()
            )) {

                if (payment.getDueDate() != null) {

                    unpaidPayments.add(
                            payment
                    );
                }
            }
        }

        unpaidPayments.sort(
                Comparator.comparing(
                        Payment::getDueDate
                )
        );

        List<UpcomingInstallmentResponse> upcomingInstallments =
                new ArrayList<>();

        int installmentLimit = 0;

        for (Payment payment : unpaidPayments) {

            if (installmentLimit >= 6) {
                break;
            }

            UpcomingInstallmentResponse installment =
                    UpcomingInstallmentResponse
                            .builder()
                            .installmentNumber(
                                    payment.getInstallmentNumber()
                            )
                            .dueDate(
                                    payment.getDueDate()
                            )
                            .amount(
                                    payment.getAmount()
                            )
                            .principal(
                                    payment.getPrincipalComponent()
                            )
                            .interest(
                                    payment.getInterestComponent()
                            )
                            .status(
                                    payment.getStatus() != null
                                            ? payment.getStatus().name()
                                            : "PENDING"
                            )
                            .build();

            upcomingInstallments.add(
                    installment
            );

            installmentLimit++;
        }

        // ============================================================
        // AVAILABLE PAYMENT METHODS
        // ============================================================

        List<String> paymentMethods =
                List.of(
                        "MTN Mobile Money",
                        "Airtel Money",
                        "Bank Transfer",
                        "Visa / Mastercard"
                );

        // ============================================================
        // BORROWER LOAN STATISTICS
        // ============================================================

        List<Loan> borrowerLoans =
                loanRepository
                        .findByBorrowerIdAndOrganizationId(
                                loan.getBorrower().getId(),
                                loan.getOrganization().getId()
                        );

        int activeLoans = 0;
        int overdueLoans = 0;
        int completedLoans = 0;

        for (Loan borrowerLoan : borrowerLoans) {

            if (borrowerLoan.getStatus() == null) {
                continue;
            }

            switch (borrowerLoan.getStatus()) {

                case ACTIVE:
                    activeLoans++;
                    break;

                case OVERDUE:
                    overdueLoans++;
                    break;

                case PAID:
                case CLOSED:
                    completedLoans++;
                    break;

                default:
                    break;
            }
        }

        // ============================================================
        // BUILD RESPONSE
        // ============================================================

       BorrowerDashboardResponse response =
        BorrowerDashboardResponse
                .builder()

                // ------------------------------------------------
                // LOAN
                // ------------------------------------------------

                .loanId(
                        loan.getId()
                )

                .referenceNumber(
                        loan.getReferenceNumber()
                )

                .borrowerName(
                        loan.getBorrower() != null
                                ? loan.getBorrower().getFullName()
                                : null
                )

                .loanOfficer(
                        loan.getLoanOfficer() != null
                                ? loan.getLoanOfficer().getFullName()
                                : null
                )

                .status(
                        loan.getStatus() != null
                                ? loan.getStatus().name()
                                : null
                )

                .loanType(
                        loan.getLoanType() != null
                                ? loan.getLoanType().name()
                                : null
                )

                // ------------------------------------------------
                // FINANCIAL VALUES
                // ------------------------------------------------

                .principal(
                        loan.getAmount()
                )

                .outstandingBalance(
                        loan.getOutstandingBalance()
                )

                .totalPaid(
                        loan.getTotalPaid()
                )

                .totalRepayable(
                        loan.getTotalRepayable()
                )

                .nextInstallmentAmount(
                        loan.getNextInstallmentAmount()
                )

                // ------------------------------------------------
                // DATES
                // ------------------------------------------------

                /*
                 * Loan uses nextDueDate.
                 * Public DTO exposes it as nextPaymentDate.
                 */
                .nextPaymentDate(
                        loan.getNextDueDate()
                )

                .daysUntilDue(
                        daysUntilDue
                )

                .maturityDate(
                        loan.getMaturityDate()
                )

                // ------------------------------------------------
                // INTEREST
                // ------------------------------------------------

                .interestRate(
                        loan.getInterestRate()
                )

                .interestRateType(
                        loan.getInterestRateType()
                )

                // ------------------------------------------------
                // CURRENCY
                // ------------------------------------------------

                .currency(
                        loan.getCurrency()
                )

                // ------------------------------------------------
                // DELINQUENCY
                // ------------------------------------------------

                .missedInstallments(
                        loan.getMissedInstallments()
                )

                .daysOverdue(
                        loan.getDaysOverdue()
                )

                // ------------------------------------------------
                // PAYMENT LISTS
                // ------------------------------------------------

                .recentPayments(
                        recentPayments
                )

                .upcomingInstallments(
                        upcomingInstallments
                )

                .build();
        return response;
    }
}