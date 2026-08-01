package com.patrick.fintech.loan_backend.config;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
public class ScheduledJobs {

    private final LoanRepository          loanRepo;
    private final PaymentRepository       paymentRepo;
    private final SmsService              smsService;
    private final MailService              mailService;
    private final CurrencyService         currencyService;
    private final IdempotencyKeyRepository idempotencyRepo;
    private final CollectionsService      collectionsService;
    private final AccountingService       accountingService;
    private final OrganizationRepository  organizationRepo;
    private final SchedulerLockService    lockService;

    /** End-of-day accrual run — Phase 8. For every ACTIVE/OVERDUE loan, posts one day's worth
     *  of interest as accrued-but-uncollected (DR Interest Receivable / CR Interest Income). Runs
     *  after the overdue check so a loan that flipped to OVERDUE today still accrues for today.
     *  Idempotent per organization per calendar day via IdempotencyKeyRepository — running this
     *  twice in one day (e.g. after a restart) does not double-post for any org already done. */
    @Scheduled(cron = "${app.scheduler.eod-cron:0 30 1 * * *}")
    @Transactional
    public void runEndOfDayAccruals() {
        if (!lockService.tryAcquire("eod-accrual", java.time.Duration.ofHours(2))) {
            log.info("[Scheduler] EOD accrual already running on another instance — skipping");
            return;
        }
        try {
        String key = "EOD_ACCRUAL_" + LocalDate.now();
        log.info("[Scheduler] Starting end-of-day interest accrual...");
        for (Organization org : organizationRepo.findAll()) {
            if (idempotencyRepo.findByKeyAndOrganization(key, org).isPresent()) continue;
            int posted = 0;
            List<Loan> activeLoans = loanRepo.findByStatusIn(List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))
                .stream().filter(l -> org.getId().equals(l.getOrganization().getId())).toList();
            for (Loan loan : activeLoans) {
                try {
                    double outstanding = loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : 0;
                    if (outstanding <= 0 || loan.getInterestRate() == null) continue;
                    double annualRate = "MONTHLY".equalsIgnoreCase(loan.getInterestRateType())
                        ? loan.getInterestRate() * 12 / 100.0 : loan.getInterestRate() / 100.0;
                    double dailyInterest = outstanding * annualRate / 365.0;
                    if (dailyInterest <= 0) continue;
                    accountingService.postInterestAccrual(loan, Math.round(dailyInterest * 100.0) / 100.0);
                    posted++;
                } catch (Exception e) {
                    log.warn("[Scheduler] EOD accrual failed for loan {}: {}", loan.getId(), e.getMessage());
                }
            }
            idempotencyRepo.save(IdempotencyKey.builder().key(key).organization(org)
                .endpoint("EOD_ACCRUAL").status(IdempotencyKey.Status.COMPLETED).build());
            log.info("[Scheduler] EOD accrual for org {} complete — posted interest for {} loan(s)", org.getId(), posted);
        }
        } finally {
            lockService.release("eod-accrual");
        }
    }

    /** Daily 7 AM UTC — mark overdue loans and send SMS alerts */
    @Scheduled(cron = "${app.scheduler.overdue-check-cron:0 0 7 * * *}")
    @Transactional
    public void checkOverdueLoans() {
        if (!lockService.tryAcquire("overdue-check", java.time.Duration.ofHours(1))) {
            log.info("[Scheduler] Overdue check already running on another instance — skipping");
            return;
        }
        try {
        log.info("[Scheduler] Starting daily overdue check...");
        int flagged = 0;
        List<Payment> overdue = paymentRepo.findByPaidFalseAndDueDateBefore(LocalDate.now());
        for (Payment p : overdue) {
            Loan loan = p.getLoan();
            if (loan == null) continue;
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.OVERDUE);
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(p.getDueDate(), LocalDate.now());
                loan.setDaysOverdue(Math.max(loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0, days));
                loanRepo.save(loan);
                flagged++;
                try { smsService.sendLoanOverdue(loan, loan.getDaysOverdue()); } catch (Exception e) { /* non-fatal */ }
                try { mailService.sendOverdueReminder(loan, loan.getDaysOverdue()); } catch (Exception e) { /* non-fatal */ }
            }
        }
        log.info("[Scheduler] Overdue check done: {} loans flagged", flagged);

        try {
            int cases = collectionsService.syncCasesFromOverdueLoans();
            log.info("[Scheduler] Collections queue synced: {} case(s) touched", cases);
        } catch (Exception e) {
            log.warn("[Scheduler] Collections sync failed: {}", e.getMessage());
        }
        } finally {
            lockService.release("overdue-check");
        }
    }

    /** Daily 8 AM UTC — 3-day payment due reminders */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendPaymentReminders() {
        if (!lockService.tryAcquire("payment-reminders", java.time.Duration.ofHours(1))) {
            log.info("[Scheduler] Payment reminders already running on another instance — skipping");
            return;
        }
        try {
        log.info("[Scheduler] Sending payment reminders...");
        LocalDate in3Days = LocalDate.now().plusDays(3);
        int sent = 0;
        for (Payment p : paymentRepo.findByPaidFalseAndDueDateBefore(in3Days.plusDays(1))) {
            if (p.getDueDate() == null || p.getDueDate().isBefore(LocalDate.now())) continue;
            if (!p.getDueDate().equals(in3Days)) continue;
            Loan loan = p.getLoan();
            if (loan == null || loan.getStatus() != LoanStatus.ACTIVE) continue;
            try {
                smsService.sendPaymentDue(loan, p.getAmount() != null ? p.getAmount() : 0, in3Days.toString());
                mailService.sendPaymentDueReminder(loan);
                sent++;
            } catch (Exception e) { /* non-fatal */ }
        }
        log.info("[Scheduler] Payment reminders sent: {}", sent);
        } finally {
            lockService.release("payment-reminders");
        }
    }

    /** Daily 2 AM UTC — refresh FX rates */
    @Scheduled(cron = "${app.scheduler.fx-refresh-cron:0 0 2 * * *}")
    public void refreshFxRates() {
        if (!lockService.tryAcquire("fx-refresh", java.time.Duration.ofMinutes(30))) {
            log.info("[Scheduler] FX refresh already running on another instance — skipping");
            return;
        }
        try {
        log.info("[Scheduler] Refreshing FX rates...");
        CurrencyService.RefreshResult r = currencyService.refreshRates();
        log.info("[Scheduler] FX rates {}: {}", r.success() ? "refreshed via" : "failed, using cache for", r.source());
        } finally {
            lockService.release("fx-refresh");
        }
    }

    /** Midnight daily — clean up expired idempotency keys */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupIdempotencyKeys() {
        if (!lockService.tryAcquire("idempotency-cleanup", java.time.Duration.ofMinutes(30))) {
            log.info("[Scheduler] Idempotency cleanup already running on another instance — skipping");
            return;
        }
        try {
        List<IdempotencyKey> expired = idempotencyRepo.findAll().stream()
            .filter(k -> k.getExpiresAt() != null && k.getExpiresAt().isBefore(java.time.LocalDateTime.now()))
            .toList();
        if (!expired.isEmpty()) {
            idempotencyRepo.deleteAll(expired);
            log.info("[Scheduler] Cleaned {} expired idempotency keys", expired.size());
        }
        } finally {
            lockService.release("idempotency-cleanup");
        }
    }
}
