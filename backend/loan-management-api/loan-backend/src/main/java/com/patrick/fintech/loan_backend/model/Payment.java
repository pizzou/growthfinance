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
        @Index(name = "idx_payment_transaction", columnList = "transaction_id")
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

    @Column(
        name = "payment_reference",
        unique = true,
        length = 100
    )
    private String paymentReference;

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

    @Column(name = "installment_number")
    private Integer installmentNumber;

    /**
     * Scheduled installment amount.
     */
    @Column(name = "amount")
    private Double amount;

    /**
     * Principal component allocated from the current payment.
     *
     * IMPORTANT:
     * This represents the principal allocated by the latest recording
     * against this cycle, not necessarily the entire lifetime principal
     * reduction of the cycle.
     */
    @Column(name = "principal_component")
    private Double principalComponent;

    /**
     * Interest component allocated from the current payment.
     */
    @Column(name = "interest_component")
    private Double interestComponent;

    /**
     * Total amount paid against this cycle/installment so far.
     *
     * This is cumulative when multiple payments are made against the
     * same cycle.
     */
    @Column(name = "amount_paid")
    private Double amountPaid;

    @Column(name = "penalty")
    @Builder.Default
    private Double penalty = 0.0;

    @Column(name = "waived_amount")
    @Builder.Default
    private Double waivedAmount = 0.0;

    @Column(name = "outstanding_after")
    private Double outstandingAfter;

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * Monthly interest obligation established for this payment cycle.
     *
     * This value is calculated ONCE when the cycle receives its first
     * payment and remains unchanged for subsequent partial payments.
     */
    @Column(name = "cycle_interest_due")
    private Double cycleInterestDue;

    /**
     * Remaining interest obligation for this cycle.
     *
     * Example:
     *
     * cycleInterestDue = 30,000
     * first payment interest = 10,000
     * cycleInterestRemaining = 20,000
     *
     * A later payment will use this remaining amount before reducing
     * principal.
     */
    @Column(name = "cycle_interest_remaining")
    private Double cycleInterestRemaining;

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

    public enum PaymentStatus {

        PENDING,

        COMPLETED,

        FAILED,

        REVERSED,

        PARTIALLY_PAID
    }
}