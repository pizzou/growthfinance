
package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.service.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobs {

    private final LoanRepository loanRepo;
    private final PaymentRepository paymentRepo;
    private final SmsService smsService;
    private final MailService mailService;
    private final CurrencyService currencyService;
    private final IdempotencyKeyRepository idempotencyRepo;
    private final CollectionsService collectionsService;
    private final AccountingService accountingService;
    private final OrganizationRepository organizationRepo;
    private final SchedulerLockService lockService;
    private final LoanClassificationService loanClassificationService;


    // ================================================================
    // 1. END-OF-DAY INTEREST ACCRUAL
    // ================================================================

    @Scheduled(cron = "${app.scheduler.eod-cron:0 30 1 * * *}")
    @Transactional
    public void runEndOfDayAccruals() {

        if (!lockService.tryAcquire(
                "eod-accrual",
                Duration.ofHours(2))) {

            log.info(
                    "[Scheduler] EOD accrual already running on another "
                            + "instance — skipping"
            );

            return;
        }

        try {

            String key =
                    "EOD_ACCRUAL_" + LocalDate.now();

            log.info(
                    "[Scheduler] Starting end-of-day interest accrual..."
            );

            for (Organization org : organizationRepo.findAll()) {

                if (idempotencyRepo
                        .findByKeyAndOrganization(key, org)
                        .isPresent()) {

                    continue;
                }

                int posted = 0;

                List<Loan> activeLoans =
                        loanRepo
                                .findByStatusIn(
                                        List.of(
                                                LoanStatus.ACTIVE,
                                                LoanStatus.OVERDUE
                                        )
                                )
                                .stream()
                                .filter(loan ->
                                        loan.getOrganization() != null
                                                && loan.getOrganization()
                                                .getId()
                                                .equals(org.getId())
                                )
                                .toList();

                for (Loan loan : activeLoans) {

                    try {

                        // ------------------------------------------------
                        // BIGDECIMAL FINANCIAL VALUES
                        // ------------------------------------------------

                        BigDecimal outstanding =
                                loan.getOutstandingBalance() != null
                                        ? loan.getOutstandingBalance()
                                        : BigDecimal.ZERO;

                        if (outstanding.compareTo(
                                BigDecimal.ZERO) <= 0) {

                            continue;
                        }

                        BigDecimal interestRate =
                                loan.getInterestRate();

                        if (interestRate == null
                                || interestRate.compareTo(
                                BigDecimal.ZERO) <= 0) {

                            continue;
                        }

                        // ------------------------------------------------
                        // DETERMINE ANNUAL RATE
                        // ------------------------------------------------

                        BigDecimal annualRate;

                        if ("MONTHLY".equalsIgnoreCase(
                                loan.getInterestRateType())) {

                            annualRate =
                                    interestRate
                                            .multiply(
                                                    BigDecimal.valueOf(12)
                                            )
                                            .divide(
                                                    BigDecimal.valueOf(100),
                                                    10,
                                                    RoundingMode.HALF_UP
                                            );

                        } else {

                            annualRate =
                                    interestRate
                                            .divide(
                                                    BigDecimal.valueOf(100),
                                                    10,
                                                    RoundingMode.HALF_UP
                                            );
                        }

                        // ------------------------------------------------
                        // DAILY INTEREST
                        // ------------------------------------------------

                        BigDecimal dailyInterest =
                                outstanding
                                        .multiply(annualRate)
                                        .divide(
                                                BigDecimal.valueOf(365),
                                                10,
                                                RoundingMode.HALF_UP
                                        )
                                        .setScale(
                                                2,
                                                RoundingMode.HALF_UP
                                        );

                        if (dailyInterest.compareTo(
                                BigDecimal.ZERO) <= 0) {

                            continue;
                        }

                        // ------------------------------------------------
                        // ACCOUNTING SERVICE
                        //
                        // Existing AccountingService expects double.
                        // Convert only at the service boundary.
                        // ------------------------------------------------

                        accountingService.postInterestAccrual(
                                loan,
                                dailyInterest.doubleValue()
                        );

                        posted++;

                    } catch (Exception e) {

                        log.warn(
                                "[Scheduler] EOD accrual failed for "
                                        + "loan {}: {}",
                                loan.getId(),
                                e.getMessage(),
                                e
                        );
                    }
                }

                idempotencyRepo.save(
                        IdempotencyKey.builder()
                                .key(key)
                                .organization(org)
                                .endpoint("EOD_ACCRUAL")
                                .status(
                                        IdempotencyKey.Status.COMPLETED
                                )
                                .build()
                );

                log.info(
                        "[Scheduler] EOD accrual for org {} complete "
                                + "— posted interest for {} loan(s)",
                        org.getId(),
                        posted
                );
            }

        } finally {

            lockService.release("eod-accrual");
        }
    }


    // ================================================================
    // 2. CHECK OVERDUE LOANS
    // ================================================================

    @Scheduled(
            cron = "${app.scheduler.overdue-check-cron:0 0 7 * * *}"
    )
    @Transactional
    public void checkOverdueLoans() {

        if (!lockService.tryAcquire(
                "overdue-check",
                Duration.ofHours(1))) {

            log.info(
                    "[Scheduler] Overdue check already running on "
                            + "another instance — skipping"
            );

            return;
        }

        try {

            log.info(
                    "[Scheduler] Starting daily overdue check..."
            );

            int flagged = 0;

            List<Payment> overdue =
                    paymentRepo
                            .findByPaidFalseAndDueDateBefore(
                                    LocalDate.now()
                            );

            for (Payment payment : overdue) {

                Loan loan =
                        payment.getLoan();

                if (loan == null) {
                    continue;
                }

                if (loan.getStatus() == LoanStatus.ACTIVE) {

                    loan.setStatus(
                            LoanStatus.OVERDUE
                    );

                    int days =
                            (int) ChronoUnit.DAYS.between(
                                    payment.getDueDate(),
                                    LocalDate.now()
                            );

                    int existingDays =
                            loan.getDaysOverdue() != null
                                    ? loan.getDaysOverdue()
                                    : 0;

                    loan.setDaysOverdue(
                            Math.max(
                                    existingDays,
                                    days
                            )
                    );

                    loanRepo.save(loan);

                    flagged++;

                    try {

                        smsService.sendLoanOverdue(
                                loan,
                                loan.getDaysOverdue()
                        );

                    } catch (Exception e) {

                        log.warn(
                                "[Scheduler] Overdue SMS failed "
                                        + "for loan {}: {}",
                                loan.getId(),
                                e.getMessage()
                        );
                    }

                    try {

                        mailService.sendOverdueReminder(
                                loan,
                                loan.getDaysOverdue()
                        );

                    } catch (Exception e) {

                        log.warn(
                                "[Scheduler] Overdue email failed "
                                        + "for loan {}: {}",
                                loan.getId(),
                                e.getMessage()
                        );
                    }
                }
            }

            log.info(
                    "[Scheduler] Overdue check done: {} loans flagged",
                    flagged
            );


            // ------------------------------------------------------------
            // COLLECTIONS
            // ------------------------------------------------------------

            try {

                int cases =
                        collectionsService
                                .syncCasesFromOverdueLoans();

                log.info(
                        "[Scheduler] Collections queue synced: "
                                + "{} case(s) touched",
                        cases
                );

            } catch (Exception e) {

                log.warn(
                        "[Scheduler] Collections sync failed: {}",
                        e.getMessage(),
                        e
                );
            }


            // ------------------------------------------------------------
            // PORTFOLIO RECLASSIFICATION
            // ------------------------------------------------------------

            try {

                int totalReclassified = 0;

                for (Organization org :
                        organizationRepo.findAll()) {

                    totalReclassified +=
                            loanClassificationService
                                    .reclassifyPortfolio(org);
                }

                log.info(
                        "[Scheduler] Portfolio reclassification done: "
                                + "{} loan(s) changed classification",
                        totalReclassified
                );

            } catch (Exception e) {

                log.warn(
                        "[Scheduler] Portfolio reclassification failed: {}",
                        e.getMessage(),
                        e
                );
            }

        } finally {

            lockService.release("overdue-check");
        }
    }


    // ================================================================
    // 3. PAYMENT REMINDERS
    // ================================================================

    /**
     * Daily 8 AM UTC — 3-day payment due reminders.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendPaymentReminders() {

        if (!lockService.tryAcquire(
                "payment-reminders",
                Duration.ofHours(1))) {

            log.info(
                    "[Scheduler] Payment reminders already running "
                            + "on another instance — skipping"
            );

            return;
        }

        try {

            log.info(
                    "[Scheduler] Sending payment reminders..."
            );

            LocalDate today =
                    LocalDate.now();

            LocalDate in3Days =
                    today.plusDays(3);

            int sent = 0;

            List<Payment> payments =
                    paymentRepo
                            .findByPaidFalseAndDueDateBefore(
                                    in3Days.plusDays(1)
                            );

            for (Payment payment : payments) {

                if (payment.getDueDate() == null) {
                    continue;
                }

                if (payment.getDueDate().isBefore(today)) {
                    continue;
                }

                if (!payment.getDueDate().equals(in3Days)) {
                    continue;
                }

                Loan loan =
                        payment.getLoan();

                if (loan == null) {
                    continue;
                }

                if (loan.getStatus() != LoanStatus.ACTIVE) {
                    continue;
                }

                try {

                    // ------------------------------------------------
                    // Payment.amount is BigDecimal.
                    // SmsService.sendPaymentDue expects double.
                    // Convert only at the boundary.
                    // ------------------------------------------------

                    double amount =
                            payment.getAmount() != null
                                    ? payment.getAmount()
                                    .doubleValue()
                                    : 0.0;

                    smsService.sendPaymentDue(
                            loan,
                            amount,
                            in3Days.toString()
                    );

                    mailService.sendPaymentDueReminder(
                            loan
                    );

                    sent++;

                } catch (Exception e) {

                    log.warn(
                            "[Scheduler] Payment reminder failed "
                                    + "for loan {}: {}",
                            loan.getId(),
                            e.getMessage()
                    );
                }
            }

            log.info(
                    "[Scheduler] Payment reminders sent: {}",
                    sent
            );

        } finally {

            lockService.release("payment-reminders");
        }
    }


    // ================================================================
    // 4. REFRESH FX RATES
    // ================================================================

    /**
     * Daily 2 AM UTC — refresh FX rates.
     */
    @Scheduled(
            cron = "${app.scheduler.fx-refresh-cron:0 0 2 * * *}"
    )
    public void refreshFxRates() {

        if (!lockService.tryAcquire(
                "fx-refresh",
                Duration.ofMinutes(30))) {

            log.info(
                    "[Scheduler] FX refresh already running on "
                            + "another instance — skipping"
            );

            return;
        }

        try {

            log.info(
                    "[Scheduler] Refreshing FX rates..."
            );

            CurrencyService.RefreshResult result =
                    currencyService.refreshRates();

            log.info(
                    "[Scheduler] FX rates {}: {}",
                    result.success()
                            ? "refreshed via"
                            : "failed, using cache for",
                    result.source()
            );

        } finally {

            lockService.release("fx-refresh");
        }
    }


    // ================================================================
    // 5. CLEANUP IDEMPOTENCY KEYS
    // ================================================================

    /**
     * Midnight daily — clean up expired idempotency keys.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupIdempotencyKeys() {

        if (!lockService.tryAcquire(
                "idempotency-cleanup",
                Duration.ofMinutes(30))) {

            log.info(
                    "[Scheduler] Idempotency cleanup already "
                            + "running on another instance — skipping"
            );

            return;
        }

        try {

            LocalDateTime now =
                    LocalDateTime.now();

            List<IdempotencyKey> expired =
                    idempotencyRepo
                            .findAll()
                            .stream()
                            .filter(key ->
                                    key.getExpiresAt() != null
                                            && key.getExpiresAt()
                                            .isBefore(now)
                            )
                            .toList();

            if (!expired.isEmpty()) {

                idempotencyRepo.deleteAll(expired);

                log.info(
                        "[Scheduler] Cleaned {} expired "
                                + "idempotency keys",
                        expired.size()
                );
            }

        } finally {

            lockService.release(
                    "idempotency-cleanup"
            );
        }
    }
}
