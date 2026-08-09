
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
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicPortalService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialCalculationService financialCalculationService;

    /**
     * Public borrower dashboard.
     *
     * IMPORTANT:
     *
     * The public portal must reflect the same daily-interest logic
     * used by PaymentService.
     *
     * It must NOT simply display loan.getNextInstallmentAmount(),
     * because that value belongs to the original repayment schedule
     * and may not represent the amount currently payable after
     * principal reductions and daily interest accrual.
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
        // ALL PAYMENT RECORDS
        //
        // We need these because the PaymentService uses the payment
        // records to determine:
        //
        // - current installment
        // - previous interest timestamp
        // - interest already paid
        // - cycle interest already accrued
        // - penalty already recorded
        //------------------------------------------------------------

        List<Payment> loanPayments =
                paymentRepository.findByLoanId(
                        loan.getId());

        //------------------------------------------------------------
        // FIND CURRENT PAYMENT CYCLE
        //
        // Same basic selection logic used by PaymentService:
        //
        // 1. Existing partially-paid cycle
        // 2. Otherwise earliest unpaid installment
        //------------------------------------------------------------

        Optional<Payment> existingCurrentCycle =
                loanPayments.stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(
                                        payment.getPaid())
                                        && safe(
                                        payment.getAmountPaid())
                                        > 0.0)
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder())));

        Optional<Payment> unpaidInstallment =
                loanPayments.stream()
                        .filter(payment ->
                                !Boolean.TRUE.equals(
                                        payment.getPaid()))
                        .min(
                                Comparator.comparing(
                                        Payment::getDueDate,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder())));

        Payment currentInstallment = null;

        if (existingCurrentCycle.isPresent()) {

            currentInstallment =
                    existingCurrentCycle.get();

        } else if (unpaidInstallment.isPresent()) {

            currentInstallment =
                    unpaidInstallment.get();
        }

        //------------------------------------------------------------
        // CALCULATE CURRENT PAYABLE AMOUNT
        //------------------------------------------------------------

        double currentPayableAmount =
                calculateCurrentPayableAmount(
                        loan,
                        currentInstallment,
                        loanPayments);

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
                loanPayments
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
        //------------------------------------------------------------

        Long borrowerId = null;

        if (loan.getBorrower() != null) {

            borrowerId =
                    loan.getBorrower().getId();
        }

        //------------------------------------------------------------
        // ORGANIZATION
        //------------------------------------------------------------

        Long organizationId = null;

        if (loan.getOrganization() != null) {

            organizationId =
                    loan.getOrganization().getId();
        }

        //------------------------------------------------------------
        // ALL BORROWER LOANS
        //------------------------------------------------------------

        List<Loan> borrowerLoans =
                borrowerId != null
                        && organizationId != null
                        ? loanRepository
                        .findByBorrowerIdAndOrganizationId(
                                borrowerId,
                                organizationId)
                        : List.of();

        //------------------------------------------------------------
        // LOAN STATISTICS
        //------------------------------------------------------------

        int activeLoans = 0;
        int overdueLoans = 0;
        int completedLoans = 0;

        for (Loan borrowerLoan :
                borrowerLoans) {

            if (borrowerLoan.getStatus() == null) {
                continue;
            }

            switch (
                    borrowerLoan
                            .getStatus()
                            .name()) {

                case "ACTIVE" ->
                        activeLoans++;

                case "OVERDUE" ->
                        overdueLoans++;

                case "PAID", "CLOSED" ->
                        completedLoans++;

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
        //
        // CRITICAL:
        //
        // nextInstallmentAmount now uses the current daily-interest
        // payable amount instead of blindly using the original
        // scheduled installment.
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
                        currentPayableAmount)

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

    //==============================================================
    // CURRENT PAYABLE AMOUNT
    //==============================================================

    /**
     * Calculates the amount currently payable by the borrower
     * using the same daily-interest rules implemented by
     * PaymentService.
     *
     * PAYMENT ORDER:
     *
     * 1. Penalty
     * 2. Interest
     * 3. Principal
     *
     * This method does NOT modify the database.
     *
     * It is read-only and exists only so the public dashboard
     * displays the amount that PaymentService would currently
     * work against.
     */
    private double calculateCurrentPayableAmount(
            Loan loan,
            Payment installment,
            List<Payment> loanPayments) {

        //----------------------------------------------------------
        // OUTSTANDING PRINCIPAL
        //----------------------------------------------------------

        double currentBalance =
                safe(
                        loan.getOutstandingBalance());

        currentBalance =
                Math.max(
                        0.0,
                        roundMoney(
                                currentBalance));

        if (currentBalance <= 0.0) {

            return 0.0;
        }

        //----------------------------------------------------------
        // IF THERE IS NO PAYMENT RECORD YET
        //
        // Use the loan's existing scheduled amount as the base
        // because there is no current payment cycle to inspect.
        //----------------------------------------------------------

        if (installment == null) {

            double scheduledAmount =
                    safe(
                            loan.getNextInstallmentAmount());

            return roundMoney(
                    Math.min(
                            Math.max(
                                    0.0,
                                    scheduledAmount),
                            currentBalance
                    )
            );
        }

        //----------------------------------------------------------
        // CURRENT DATE/TIME
        //----------------------------------------------------------

        LocalDate today =
                LocalDate.now();

        LocalDateTime now =
                LocalDateTime.now();

        //----------------------------------------------------------
        // EXISTING VALUES
        //----------------------------------------------------------

        double amountPaidSoFar =
                safe(
                        installment.getAmountPaid());

        double interestAlreadyPaid =
                safe(
                        installment.getInterestComponent());

        interestAlreadyPaid =
                Math.max(
                        0.0,
                        roundMoney(
                                interestAlreadyPaid));

        double penaltyAlreadyRecorded =
                safe(
                        installment.getPenalty());

        penaltyAlreadyRecorded =
                Math.max(
                        0.0,
                        roundMoney(
                                penaltyAlreadyRecorded));

        double existingCycleInterestDue =
                safe(
                        installment.getCycleInterestDue());

        existingCycleInterestDue =
                Math.max(
                        0.0,
                        roundMoney(
                                existingCycleInterestDue));

        //----------------------------------------------------------
        // INTEREST START TIMESTAMP
        //
        // Same priority as PaymentService.
        //----------------------------------------------------------

        LocalDateTime interestStartDateTime =
                determineInterestStartDateTime(
                        installment,
                        loan,
                        loanPayments,
                        now);

        if (interestStartDateTime.isAfter(now)) {

            interestStartDateTime = now;
        }

        //----------------------------------------------------------
        // ELAPSED HOURS
        //----------------------------------------------------------

        long elapsedHours =
                ChronoUnit.HOURS.between(
                        interestStartDateTime,
                        now);

        if (elapsedHours < 0) {

            elapsedHours = 0;
        }

        //----------------------------------------------------------
        // FIRST INTEREST CALCULATION
        //
        // Same rule as PaymentService:
        //
        // First day exists immediately after disbursement.
        //
        // Therefore:
        //
        // Disbursed 10:00
        // Paid 10:10
        // = 1 interest day
        //----------------------------------------------------------

        boolean firstInterestCalculation =
                installment.getInterestCalculationDate() == null
                        && amountPaidSoFar <= 0.0
                        && interestAlreadyPaid <= 0.0
                        && existingCycleInterestDue <= 0.0;

        long elapsedDays;

        if (firstInterestCalculation) {

            elapsedDays =
                    Math.max(
                            1L,
                            elapsedHours / 24L);

        } else {

            elapsedDays =
                    elapsedHours / 24L;
        }

        //----------------------------------------------------------
        // DAILY RATE
        //----------------------------------------------------------

        java.math.BigDecimal dailyRateDecimal =
                financialCalculationService.dailyRate(
                        loan.getInterestRate(),
                        loan.getInterestRateType());
        double dailyRate = dailyRateDecimal.doubleValue();

        //----------------------------------------------------------
        // NEW INTEREST
        //----------------------------------------------------------

        double newlyAccruedInterest =
                financialCalculationService
                        .interest(
                                financialCalculationService.money(currentBalance),
                                dailyRateDecimal,
                                elapsedDays)
                        .doubleValue();

        newlyAccruedInterest =
                Math.max(
                        0.0,
                        newlyAccruedInterest);

        //----------------------------------------------------------
        // TOTAL CYCLE INTEREST
        //----------------------------------------------------------

        double totalCycleInterestDue =
                roundMoney(
                        existingCycleInterestDue
                                + newlyAccruedInterest);

        totalCycleInterestDue =
                Math.max(
                        0.0,
                        totalCycleInterestDue);

        //----------------------------------------------------------
        // REMAINING INTEREST
        //----------------------------------------------------------

        double remainingInterest =
                roundMoney(
                        Math.max(
                                0.0,
                                totalCycleInterestDue
                                        - interestAlreadyPaid));

        //----------------------------------------------------------
        // PENALTY
        //
        // Same rule as PaymentService:
        //
        // 2% per 30 days
        // daily = 0.02 / 30
        //----------------------------------------------------------

        int daysLate = 0;

        LocalDate cycleDueDate =
                installment.getDueDate() != null
                        ? installment.getDueDate()
                        : (
                        loan.getNextDueDate() != null
                                ? loan.getNextDueDate()
                                : today
                );

        if (cycleDueDate != null) {

            long daysLateLong =
                    ChronoUnit.DAYS.between(
                            cycleDueDate,
                            today);

            daysLate =
                    (int)
                            Math.max(
                                    0L,
                                    daysLateLong);
        }

        double dailyPenaltyRate =
                0.02 / 30.0;

        double calculatedPenalty =
                financialCalculationService
                        .penalty(
                                financialCalculationService.money(currentBalance),
                                daysLate)
                        .doubleValue();

        //----------------------------------------------------------
        // ONLY NEW PENALTY
        //----------------------------------------------------------

        double newPenalty =
                Math.max(
                        0.0,
                        roundMoney(
                                calculatedPenalty
                                        - penaltyAlreadyRecorded));

        //----------------------------------------------------------
        // CURRENT PAYABLE AMOUNT
        //
        // The borrower must cover:
        //
        // - new penalty
        // - remaining interest
        // - principal obligation
        //
        // Principal component is the outstanding principal.
        //
        // Therefore the current amount required to completely
        // settle the loan is:
        //
        // outstanding principal
        // + remaining interest
        // + new penalty
        //----------------------------------------------------------

        double currentPayable =
                roundMoney(
                        currentBalance
                                + remainingInterest
                                + newPenalty);

        //----------------------------------------------------------
        // SAFETY
        //----------------------------------------------------------

        if (currentPayable < 0.0) {

            currentPayable = 0.0;
        }

        log.debug(
                "PUBLIC DASHBOARD PAYMENT CALCULATION: loanId={}, principal={}, dailyRate={}, start={}, now={}, elapsedHours={}, interestDays={}, existingInterest={}, newInterest={}, remainingInterest={}, penalty={}, currentPayable={}",
                loan.getId(),
                currentBalance,
                dailyRate,
                interestStartDateTime,
                now,
                elapsedHours,
                elapsedDays,
                existingCycleInterestDue,
                newlyAccruedInterest,
                remainingInterest,
                newPenalty,
                currentPayable
        );

        return currentPayable;
    }

    //==============================================================
    // DETERMINE INTEREST START
    //==============================================================

    private LocalDateTime determineInterestStartDateTime(
            Payment installment,
            Loan loan,
            List<Payment> loanPayments,
            LocalDateTime now) {

        //----------------------------------------------------------
        // 1. CURRENT INSTALLMENT TIMESTAMP
        //----------------------------------------------------------

        if (installment.getInterestCalculationDate() != null) {

            return installment
                    .getInterestCalculationDate();
        }

        //----------------------------------------------------------
        // 2. MOST RECENT PAYMENT TIMESTAMP
        //----------------------------------------------------------

        LocalDateTime latestTimestamp =
                findLatestInterestCalculationTimestamp(
                        loanPayments,
                        null);

        if (latestTimestamp != null) {

            return latestTimestamp;
        }

        //----------------------------------------------------------
        // 3. EXACT DISBURSEMENT TIMESTAMP
        //----------------------------------------------------------

        if (loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        //----------------------------------------------------------
        // 4. PAYMENT CREATED TIMESTAMP
        //----------------------------------------------------------

        if (installment.getCreatedAt() != null) {

            return installment.getCreatedAt();
        }

        //----------------------------------------------------------
        // 5. LOAN START DATE
        //----------------------------------------------------------

        if (loan.getStartDate() != null) {

            return loan
                    .getStartDate()
                    .atStartOfDay();
        }

        //----------------------------------------------------------
        // 6. FINAL FALLBACK
        //----------------------------------------------------------

        return now;
    }

    //==============================================================
    // FIND LATEST INTEREST TIMESTAMP
    //==============================================================

    private LocalDateTime findLatestInterestCalculationTimestamp(
            List<Payment> payments,
            Loan loan) {

        Optional<LocalDateTime> latest =
                payments.stream()
                        .map(
                                Payment::getInterestCalculationDate)
                        .filter(
                                timestamp ->
                                        timestamp != null)
                        .max(
                                LocalDateTime::compareTo);

        if (latest.isPresent()) {

            return latest.get();
        }

        //----------------------------------------------------------
        // FALLBACK TO DISBURSEMENT
        //----------------------------------------------------------

        if (loan != null
                && loan.getDisbursedAt() != null) {

            return loan.getDisbursedAt();
        }

        return null;
    }

    //==============================================================
    // DAILY INTEREST RATE
    //==============================================================

    /**
     * Exactly the same daily-rate conversion used by
     * PaymentService.
     */
    private double calculateDailyRate(
            Loan loan) {
        return financialCalculationService
                .dailyRate(
                        loan.getInterestRate(),
                        loan.getInterestRateType())
                .doubleValue();
    }

    //==============================================================
    // PAYMENT HISTORY MAPPER
    //==============================================================

    private PaymentHistoryResponse
    toPaymentHistoryResponse(
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
                                : payment
                                .getStatus()
                                .name())

                .build();
    }

    //==============================================================
    // UPCOMING INSTALLMENT MAPPER
    //==============================================================

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
                                : payment
                                .getStatus()
                                .name())

                .build();
    }

    //==============================================================
    // SAFE DOUBLE
    //==============================================================

    private double safe(
            Double value) {

        if (value == null
                || Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0.0;
        }

        return value;
    }

    //==============================================================
    // ROUND MONEY
    //==============================================================

    private double roundMoney(
            double value) {

        if (Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0.0;
        }

        return Math.round(
                value * 100.0)
                / 100.0;
    }
}
