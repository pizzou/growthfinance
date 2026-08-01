package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkDisbursementService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final LoanRepository loanRepo;
    private final AuditService auditService;
    private final WebhookService webhookService;
    private final SmsService smsService;

    /**
     * Disburse multiple approved loans in one operation.
     */
    @Transactional
    public BulkDisbursementResult disburseAll(
            List<Long> loanIds,
            Long orgId,
            User officer,
            String method) {

        List<DisbursementLine> lines = new ArrayList<>();

        BigDecimal total = BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                MONEY_ROUNDING
        );

        int ok = 0;
        int fail = 0;

        if (loanIds == null || loanIds.isEmpty()) {
            return new BulkDisbursementResult(
                    0,
                    0,
                    total,
                    method,
                    LocalDateTime.now(),
                    lines
            );
        }

        for (Long id : loanIds) {

            try {

                Loan loan =
                        loanRepo.findById(id)
                                .orElseThrow(() ->
                                        new RuntimeException(
                                                "Loan not found: " + id
                                        )
                                );

                /*
                 * Organization security check.
                 */
                if (loan.getOrganization() == null
                        || loan.getOrganization().getId() == null
                        || !loan.getOrganization()
                                .getId()
                                .equals(orgId)) {

                    lines.add(
                            DisbursementLine.failed(
                                    id,
                                    null,
                                    "Access denied"
                            )
                    );

                    fail++;
                    continue;
                }

                /*
                 * Only APPROVED loans can be bulk disbursed.
                 */
                if (loan.getStatus() != LoanStatus.APPROVED) {

                    lines.add(
                            DisbursementLine.failed(
                                    id,
                                    loan.getReferenceNumber(),
                                    "Status is "
                                            + loan.getStatus()
                            )
                    );

                    fail++;
                    continue;
                }

                /*
                 * Validate loan amount.
                 */
                BigDecimal amount =
                        loan.getAmount() != null
                                ? loan.getAmount()
                                : BigDecimal.ZERO;

                amount = amount.setScale(
                        MONEY_SCALE,
                        MONEY_ROUNDING
                );

                if (amount.compareTo(BigDecimal.ZERO) <= 0) {

                    lines.add(
                            DisbursementLine.failed(
                                    id,
                                    loan.getReferenceNumber(),
                                    "Loan amount must be greater than zero"
                            )
                    );

                    fail++;
                    continue;
                }

                /*
                 * Perform disbursement.
                 */
                LocalDate today = LocalDate.now();

                loan.setStatus(LoanStatus.ACTIVE);

                loan.setDisbursedAt(today);

                loan.setDisbursedAmount(amount);

                loan.setMaturityDate(
                        today.plusMonths(
                                loan.getDurationMonths()
                        )
                );

                loan.setNextDueDate(
                        today.plusMonths(1)
                );

                Loan savedLoan =
                        loanRepo.save(loan);

                /*
                 * Add to total using BigDecimal.
                 *
                 * OLD:
                 * total += loan.getAmount()
                 *
                 * NEW:
                 * total = total.add(amount)
                 */
                total = total.add(amount);

                ok++;

                /*
                 * Add successful result line.
                 */
                lines.add(
                        DisbursementLine.success(
                                savedLoan.getId(),
                                savedLoan.getReferenceNumber(),
                                amount,
                                savedLoan.getCurrency()
                        )
                );

                /*
                 * Send SMS notification.
                 *
                 * Notification failure should not make the
                 * successful disbursement fail.
                 */
                try {
                    smsService.sendLoanApproved(savedLoan);
                } catch (Exception e) {
                    log.warn(
                            "SMS failed for bulk disbursement loan {}: {}",
                            savedLoan.getId(),
                            e.getMessage()
                    );
                }

                /*
                 * Audit.
                 */
                auditService.log(
                        savedLoan.getOrganization(),
                        officer,
                        "BULK_DISBURSEMENT",
                        "LOAN",
                        savedLoan.getId().toString(),
                        "Bulk disbursed via " + method
                );

                /*
                 * Webhook.
                 */
                try {
                    webhookService.dispatch(
                            savedLoan.getOrganization(),
                            "LOAN_DISBURSED",
                            savedLoan
                    );
                } catch (Exception e) {
                    log.warn(
                            "Webhook failed for bulk disbursement loan {}: {}",
                            savedLoan.getId(),
                            e.getMessage()
                    );
                }

            } catch (Exception e) {

                log.warn(
                        "Bulk disbursement failed for loan {}: {}",
                        id,
                        e.getMessage()
                );

                lines.add(
                        DisbursementLine.failed(
                                id,
                                null,
                                e.getMessage() != null
                                        ? e.getMessage()
                                        : "Unknown error"
                        )
                );

                fail++;
            }
        }

        log.info(
                "Bulk disbursement: {}/{} succeeded, total {}",
                ok,
                loanIds.size(),
                total
        );

        return new BulkDisbursementResult(
                ok,
                fail,
                total,
                method,
                LocalDateTime.now(),
                lines
        );
    }

    /**
     * One line in the bulk-disbursement result.
     *
     * Money is BigDecimal because Loan.amount is BigDecimal.
     */
    public record DisbursementLine(
            Long loanId,
            String referenceNumber,
            boolean success,
            BigDecimal amount,
            String currency,
            String errorMessage) {

        public static DisbursementLine success(
                Long id,
                String ref,
                BigDecimal amt,
                String cur) {

            return new DisbursementLine(
                    id,
                    ref,
                    true,
                    amt,
                    cur,
                    null
            );
        }

        public static DisbursementLine failed(
                Long id,
                String ref,
                String err) {

            return new DisbursementLine(
                    id,
                    ref,
                    false,
                    null,
                    null,
                    err
            );
        }
    }

    /**
     * Complete bulk-disbursement result.
     */
    public record BulkDisbursementResult(
            int successCount,
            int failureCount,
            BigDecimal totalAmountDisbursed,
            String disbursementMethod,
            LocalDateTime processedAt,
            List<DisbursementLine> lines) {
    }
}