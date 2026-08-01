package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository  paymentRepo;
    private final LoanRepository     loanRepo;
    private final AuditLogRepository auditRepo;
    private final AuditService auditService;
    private final UserRepository     userRepo;
    private final NotificationService notifService;
    private final MailService         mailService;
    private final SmsService            smsService;
    private final WebhookService      webhookService;
    private final AccountingService   accountingService;
    private final LoanClassificationService loanClassificationService;

    @Transactional
    public Payment recordPayment(Long loanId, Double amount, String method,
                                  String txnId, String channel, String notes,
                                  User recordedBy) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

        
        if (recordedBy != null && !loan.getOrganization().getId().equals(recordedBy.getOrganization().getId()))
            throw new RuntimeException("Access denied");

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE)
            throw new RuntimeException("Loan is not active (status: " + loan.getStatus() + ")");

       
        Optional<Payment> nextInstallmentOpt = paymentRepo.findByLoanId(loanId).stream()
            .filter(p -> !p.getPaid())
            .min(java.util.Comparator.comparing(Payment::getDueDate));

        LocalDate cycleDueDate = nextInstallmentOpt.map(Payment::getDueDate)
            .orElse(loan.getNextDueDate() != null ? loan.getNextDueDate() : LocalDate.now());

        boolean isLate = LocalDate.now().isAfter(cycleDueDate);
        int daysLate = isLate
            ? (int) java.time.temporal.ChronoUnit.DAYS.between(cycleDueDate, LocalDate.now())
            : 0;

        double penalty   = isLate ? amount * 0.02 * daysLate / 30 : 0;
        double netAvailable = Math.max(0, amount - penalty);
        double balance   = loan.getOutstandingBalance() != null ? loan.getOutstandingBalance() : 0;

        
        double rate = loan.getInterestRate() != null ? loan.getInterestRate() : 0.0;
        String rateType = loan.getInterestRateType() != null ? loan.getInterestRateType() : "MONTHLY";
        double monthlyRate = "MONTHLY".equalsIgnoreCase(rateType) ? rate / 100.0 : rate / 100.0 / 12.0;
        double interestDue = round(balance * monthlyRate);

        double interestPaid  = Math.min(netAvailable, interestDue);
        double principalPaid = Math.min(netAvailable - interestPaid, balance);
        double newBalance    = round(Math.max(0, balance - principalPaid));

       
        boolean interestCovered = netAvailable >= interestDue - 0.01;
        boolean fullyPaidOff    = newBalance <= 0.01;

        Payment installment = nextInstallmentOpt.orElse(null);
        if (installment == null) {
            int nextNumber = paymentRepo.findByLoanId(loanId).size() + 1;
            installment = Payment.builder()
                .loan(loan)
                .organization(loan.getOrganization())
                .installmentNumber(nextNumber)
                .dueDate(cycleDueDate)
                .amountPaid(0.0)
                .build();
        }

        installment.setPaid(interestCovered || fullyPaidOff);
        installment.setPaidDate(LocalDate.now());
        installment.setAmountPaid(round((installment.getAmountPaid() != null ? installment.getAmountPaid() : 0) + amount));
        installment.setPrincipalComponent(round(principalPaid));
        installment.setInterestComponent(round(interestPaid));
        installment.setPenalty(round(penalty));
        installment.setOutstandingAfter(newBalance);
        installment.setLate(isLate);
        installment.setDaysLate(daysLate);
        installment.setPaymentMethod(method);
        installment.setTransactionId(txnId);
        installment.setChannel(channel);
        installment.setNotes(notes);
        installment.setStatus(interestCovered || fullyPaidOff
            ? Payment.PaymentStatus.COMPLETED
            : Payment.PaymentStatus.PARTIALLY_PAID);
        installment.setPaymentReference(generateRef(loan));
        paymentRepo.save(installment);

        // Update loan
        loan.setTotalPaid(round((loan.getTotalPaid() != null ? loan.getTotalPaid() : 0) + amount));
        loan.setOutstandingBalance(newBalance);
        loan.setLastPaymentDate(LocalDate.now());

        if (fullyPaidOff) {
            loan.setStatus(LoanStatus.PAID);
            
            Long installmentId = installment.getId();
            List<Payment> stillPending = paymentRepo.findByLoanId(loanId).stream()
                .filter(p -> !p.getPaid() && !p.getId().equals(installmentId))
                .toList();
            paymentRepo.deleteAll(stillPending);
           
            loan.setDaysOverdue(0);
        } else {
            loan.setStatus(LoanStatus.ACTIVE);
           
            if (interestCovered) loan.setDaysOverdue(0);
            
            if (interestCovered)
                loan.setNextDueDate(cycleDueDate.plusMonths(1));
        }
        loanRepo.save(loan);

        
        try { loanClassificationService.reclassify(loan); }
        catch (Exception e) { log.warn("Reclassification failed for loan {}: {}", loan.getId(), e.getMessage()); }

        audit(loan.getOrganization(), recordedBy, "PAYMENT_RECORDED", "PAYMENT",
              installment.getId().toString(),
              "Payment of " + amount + " on loan " + loan.getReferenceNumber());

        try { mailService.sendPaymentConfirmation(loan, amount); } catch (Exception e) { log.warn("Notif failed", e); }
        try { smsService.sendPaymentConfirmed(loan, amount); } catch (Exception e) { log.warn("SMS failed", e); }
       
        if (loan.getLoanOfficer() != null && (recordedBy == null || !loan.getLoanOfficer().getId().equals(recordedBy.getId()))) {
            try {
                notifService.notifyUsers(java.util.List.of(loan.getLoanOfficer()), "Payment Received",
                    "A payment of " + loan.getCurrency() + " " + amount + " was recorded on loan "
                        + loan.getReferenceNumber() + (recordedBy != null ? " by " + recordedBy.getName() : " (automatic)") + ".",
                    "success", "/dashboard/loans/" + loan.getId());
            } catch (Exception e) { log.warn("In-app notification failed", e); }
        }
        webhookService.dispatch(loan.getOrganization(), "PAYMENT_MADE", loan);
        accountingService.postPaymentReceived(installment);

        return installment;
    }

    public List<Payment> getLoanSchedule(Long loanId, Long orgId) {
        Loan loan = loanRepo.findById(loanId)
            .orElseThrow(() -> new RuntimeException("Loan not found"));
        if (!loan.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Access denied");
        return paymentRepo.findByLoanId(loanId);
    }

    /** Nightly job: flag overdue loans */
    @Transactional
    public void markOverdueLoans(Long orgId) {
        List<Payment> overduePayments = paymentRepo
            .findByOrganization_IdAndPaidFalseAndDueDateBefore(orgId, LocalDate.now());
        for (Payment p : overduePayments) {
            Loan loan = p.getLoan();
            if (loan.getStatus() == LoanStatus.ACTIVE) {
                loan.setStatus(LoanStatus.OVERDUE);
                int days = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(p.getDueDate(), LocalDate.now());
                loan.setDaysOverdue(Math.max(loan.getDaysOverdue() != null ? loan.getDaysOverdue() : 0, days));
                loanRepo.save(loan);
                try { loanClassificationService.reclassify(loan); }
                catch (Exception e) { log.warn("Reclassification failed for loan {}: {}", loan.getId(), e.getMessage()); }
            }
        }
    }

    private double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private String generateRef(Loan loan) {
        return "PAY-" + loan.getReferenceNumber() + "-" + System.currentTimeMillis() % 100000;
    }

    private void audit(Organization org, User user, String action,
                       String entityType, String entityId, String desc) {
        auditService.log(org, user, action, entityType, entityId, desc);
    }
}