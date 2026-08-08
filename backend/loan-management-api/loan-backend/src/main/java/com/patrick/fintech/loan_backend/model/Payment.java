package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "idx_payment_loan", columnList = "loan_id"),
                @Index(name = "idx_payment_due", columnList = "due_date"),
                @Index(name = "idx_payment_paid_date", columnList = "paid_date"),
                @Index(name = "idx_payment_status", columnList = "status"),
                @Index(name = "idx_payment_org", columnList = "organization_id"),
                @Index(name = "idx_payment_transaction", columnList = "transaction_id"),
                @Index(name = "idx_payment_interest_date", columnList = "interest_calculation_date")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    // ============================================================
    // IDENTITY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // PAYMENT REFERENCE
    // ============================================================

    @Column(
            name = "payment_reference",
            unique = true,
            length = 100
    )
    private String paymentReference;


    // ============================================================
    // RELATIONSHIPS
    // ============================================================

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "loan_id",
            nullable = false
    )
    private Loan loan;


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;


    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;


    // ============================================================
    // INSTALLMENT
    // ============================================================

    @Column(name = "installment_number")
    private Integer installmentNumber;


    /**
     * Scheduled installment amount.
     */
    @Column(name = "amount")
    private Double amount;


    /**
     * Cumulative principal allocated to this installment/cycle.
     */
    @Column(name = "principal_component")
    private Double principalComponent;


    /**
     * Cumulative interest actually paid for this installment/cycle.
     */
    @Column(name = "interest_component")
    private Double interestComponent;


    /**
     * Total money paid against this installment/cycle.
     *
     * This is cumulative when several payments are made.
     */
    @Column(name = "amount_paid")
    private Double amountPaid;


    // ============================================================
    // PENALTIES
    // ============================================================

    @Column(name = "penalty")
    @Builder.Default
    private Double penalty = 0.0;


    @Column(name = "waived_amount")
    @Builder.Default
    private Double waivedAmount = 0.0;


    // ============================================================
    // BALANCE
    // ============================================================

    @Column(name = "outstanding_after")
    private Double outstandingAfter;


    // ============================================================
    // PAYMENT STATUS
    // ============================================================

    @Column(name = "paid")
    @Builder.Default
    private Boolean paid = false;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            length = 30
    )
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;


    // ============================================================
    // DATES
    // ============================================================

    /**
     * Contractual/scheduled due date.
     *
     * This remains a LocalDate because the repayment schedule is
     * date-based.
     */
    @Column(name = "due_date")
    private LocalDate dueDate;


    /**
     * Calendar date on which the payment was recorded.
     */
    @Column(name = "paid_date")
    private LocalDate paidDate;


    /**
     * EXACT timestamp through which daily interest has already
     * been calculated for this payment cycle.
     *
     * IMPORTANT:
     *
     * This must be LocalDateTime rather than LocalDate because
     * interest is calculated every 24 hours.
     *
     * Example:
     *
     * Disbursement:
     *     2026-08-08 10:30:00
     *
     * Payment:
     *     2026-08-09 10:30:00
     *
     * Elapsed time:
     *     exactly 24 hours
     *
     * Therefore:
     *     1 day of interest
     *
     * After payment, this field becomes:
     *     2026-08-09 10:30:00
     *
     * A later payment at:
     *     2026-08-10 10:30:00
     *
     * calculates another 1 day.
     */
    @Column(name = "interest_calculation_date")
    private LocalDateTime interestCalculationDate;


    // ============================================================
    // LATE PAYMENT
    // ============================================================

    @Column(name = "days_late")
    @Builder.Default
    private Integer daysLate = 0;


    @Column(name = "is_late")
    @Builder.Default
    private boolean isLate = false;


    // ============================================================
    // PAYMENT DETAILS
    // ============================================================

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;


    @Column(name = "transaction_id", length = 150)
    private String transactionId;


    @Column(name = "external_reference", length = 150)
    private String externalReference;


    @Column(
            name = "gateway_response",
            columnDefinition = "TEXT"
    )
    private String gatewayResponse;


    @Column(name = "channel", length = 50)
    private String channel;


    @Column(
            name = "notes",
            columnDefinition = "TEXT"
    )
    private String notes;


    // ============================================================
    // SYSTEM DATES
    // ============================================================

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;


    // ============================================================
    // DAILY INTEREST
    // ============================================================

    /**
     * Total interest accrued for this payment cycle.
     *
     * This is NOT necessarily the amount already paid.
     *
     * Example:
     *
     * cycleInterestDue = 50,000
     * interestComponent = 20,000
     * cycleInterestRemaining = 30,000
     */
    @Column(name = "cycle_interest_due")
    private Double cycleInterestDue;


    /**
     * Interest still owed for this payment cycle.
     */
    @Column(name = "cycle_interest_remaining")
    private Double cycleInterestRemaining;


    // ============================================================
    // JPA LIFECYCLE
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (paid == null) {
            paid = false;
        }

        if (penalty == null) {
            penalty = 0.0;
        }

        if (waivedAmount == null) {
            waivedAmount = 0.0;
        }

        if (daysLate == null) {
            daysLate = 0;
        }

        if (status == null) {
            status = PaymentStatus.PENDING;
        }

        if (amountPaid == null) {
            amountPaid = 0.0;
        }

        if (principalComponent == null) {
            principalComponent = 0.0;
        }

        if (interestComponent == null) {
            interestComponent = 0.0;
        }

        if (cycleInterestDue == null) {
            cycleInterestDue = 0.0;
        }

        if (cycleInterestRemaining == null) {
            cycleInterestRemaining = 0.0;
        }

        if (daysLate > 0) {
            isLate = true;
        }
    }


    @PreUpdate
    protected void onUpdate() {

        if (daysLate != null && daysLate > 0) {
            isLate = true;
        }

        if (cycleInterestDue == null) {
            cycleInterestDue = 0.0;
        }

        if (cycleInterestRemaining == null) {
            cycleInterestRemaining = 0.0;
        }
    }


    // ============================================================
    // ENUM
    // ============================================================

    public enum PaymentStatus {

        PENDING,

        COMPLETED,

        FAILED,

        REVERSED,

        PARTIALLY_PAID
    }
}