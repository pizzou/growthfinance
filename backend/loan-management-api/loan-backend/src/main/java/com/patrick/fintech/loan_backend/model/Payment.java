package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({
    "hibernateLazyInitializer",
    "handler"
})
@Entity
@Table(
    name = "payments",
    indexes = {

        @Index(
            name = "idx_payment_loan",
            columnList = "loan_id"
        ),

        @Index(
            name = "idx_payment_due",
            columnList = "due_date"
        ),

        @Index(
            name = "idx_payment_paid_date",
            columnList = "paid_date"
        ),

        @Index(
            name = "idx_payment_status",
            columnList = "status"
        ),

        @Index(
            name = "idx_payment_org",
            columnList = "organization_id"
        ),

        @Index(
            name = "idx_payment_transaction",
            columnList = "transaction_id"
        )
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // REFERENCE
    // ============================================================

    @Column(
        name = "payment_reference",
        unique = true,
        length = 100
    )
    private String paymentReference;


    // ============================================================
    // LOAN
    // ============================================================

    @JsonIgnore
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "loan_id",
        nullable = false
    )
    private Loan loan;


    // ============================================================
    // ORGANIZATION
    // ============================================================

    @JsonIgnore
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;


    // ============================================================
    // RECORDED BY
    // ============================================================

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
     * Cumulative principal paid against this installment.
     */
    @Column(name = "principal_component")
    @Builder.Default
    private Double principalComponent = 0.0;


    /**
     * Cumulative interest paid against this installment.
     */
    @Column(name = "interest_component")
    @Builder.Default
    private Double interestComponent = 0.0;


    /**
     * Cumulative amount paid against this installment.
     */
    @Column(name = "amount_paid")
    @Builder.Default
    private Double amountPaid = 0.0;


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
    private PaymentStatus status =
        PaymentStatus.PENDING;


    // ============================================================
    // DATES
    // ============================================================

    @Column(name = "due_date")
    private LocalDate dueDate;


    @Column(name = "paid_date")
    private LocalDate paidDate;


    @Column(name = "days_late")
    @Builder.Default
    private Integer daysLate = 0;


    @Column(name = "is_late")
    @Builder.Default
    private boolean isLate = false;


    // ============================================================
    // PAYMENT INFORMATION
    // ============================================================

    @Column(
        name = "payment_method",
        length = 50
    )
    private String paymentMethod;


    @Column(
        name = "transaction_id",
        length = 150
    )
    private String transactionId;


    @Column(
        name = "external_reference",
        length = 150
    )
    private String externalReference;


    @Column(
        name = "gateway_response",
        columnDefinition = "TEXT"
    )
    private String gatewayResponse;


    @Column(
        name = "channel",
        length = 50
    )
    private String channel;


    @Column(
        name = "notes",
        columnDefinition = "TEXT"
    )
    private String notes;


    // ============================================================
    // AUDIT DATES
    // ============================================================

    @Column(name = "created_at")
    private LocalDateTime createdAt;


    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;


    // ============================================================
    // MONTHLY INTEREST STATE
    // ============================================================

    /**
     * Interest obligation for this monthly cycle.
     *
     * IMPORTANT:
     *
     * This is calculated ONCE when the cycle is first used.
     *
     * It must NOT be recalculated from the reduced loan balance
     * for every subsequent payment in the same cycle.
     */
    @Column(name = "cycle_interest_due")
    @Builder.Default
    private Double cycleInterestDue = 0.0;


    /**
     * Remaining unpaid interest for the current cycle.
     */
    @Column(name = "cycle_interest_remaining")
    @Builder.Default
    private Double cycleInterestRemaining = 0.0;


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

        if (amountPaid == null) {
            amountPaid = 0.0;
        }

        if (principalComponent == null) {
            principalComponent = 0.0;
        }

        if (interestComponent == null) {
            interestComponent = 0.0;
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

        if (amountPaid == null) {
            amountPaid = 0.0;
        }

        if (principalComponent == null) {
            principalComponent = 0.0;
        }

        if (interestComponent == null) {
            interestComponent = 0.0;
        }

        if (penalty == null) {
            penalty = 0.0;
        }
    }


    // ============================================================
    // STATUS ENUM
    // ============================================================

    public enum PaymentStatus {

        PENDING,

        COMPLETED,

        FAILED,

        REVERSED,

        PARTIALLY_PAID
    }
}