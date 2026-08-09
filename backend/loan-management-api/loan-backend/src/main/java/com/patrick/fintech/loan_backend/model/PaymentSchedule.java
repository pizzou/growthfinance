
package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_schedules",
        indexes = {
                @Index(
                        name = "idx_payment_schedules_loan_id",
                        columnList = "loan_id"
                ),
                @Index(
                        name = "idx_payment_schedules_due_date",
                        columnList = "due_date"
                ),
                @Index(
                        name = "idx_payment_schedules_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    private static final int MONEY_SCALE = 6;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE
            );

    // ============================================================
    // PRIMARY KEY
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ============================================================
    // LOAN
    // ============================================================

    /*
     * A payment schedule belongs to a loan.
     *
     * Organization is intentionally NOT stored here because
     * this application is currently single-tenant.
     *
     * If the organization is needed, it can be reached through:
     *
     * paymentSchedule.getLoan().getOrganization()
     */
    @NotNull
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
    // INSTALLMENT
    // ============================================================

    @NotNull
    @Column(
            nullable = false
    )
    private Integer installmentNumber;

    @NotNull
    @Column(
            nullable = false
    )
    private LocalDate dueDate;

    // ============================================================
    // INSTALLMENT AMOUNT
    // ============================================================

    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("installmentAmount")
    private BigDecimal installmentAmount;

    // ============================================================
    // PRINCIPAL
    // ============================================================

    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("principalAmount")
    private BigDecimal principalAmount;

    // ============================================================
    // INTEREST
    // ============================================================

    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("interestAmount")
    private BigDecimal interestAmount;

    // ============================================================
    // PENALTY
    // ============================================================

    @Builder.Default
    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("penaltyAmount")
    private BigDecimal penaltyAmount = ZERO;

    // ============================================================
    // AMOUNT PAID
    // ============================================================

    @Builder.Default
    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("amountPaid")
    private BigDecimal amountPaid = ZERO;

    // ============================================================
    // REMAINING BALANCE
    // ============================================================

    @Builder.Default
    @NotNull
    @DecimalMin(
            value = "0.000000",
            inclusive = true
    )
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("remainingBalance")
    private BigDecimal remainingBalance = ZERO;

    // ============================================================
    // STATUS
    // ============================================================

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            length = 20
    )
    private ScheduleStatus status =
            ScheduleStatus.PENDING;

    // ============================================================
    // PAID DATE
    // ============================================================

    private LocalDate paidDate;

    // ============================================================
    // AUDIT TIMESTAMPS
    // ============================================================

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            nullable = false
    )
    private LocalDateTime updatedAt;

    // ============================================================
    // PRE-PERSIST
    // ============================================================

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;

        if (status == null) {
            status = ScheduleStatus.PENDING;
        }

        installmentAmount =
                normalizeMoney(
                        installmentAmount
                );

        principalAmount =
                normalizeMoney(
                        principalAmount
                );

        interestAmount =
                normalizeMoney(
                        interestAmount
                );

        penaltyAmount =
                normalizeMoney(
                        penaltyAmount
                );

        amountPaid =
                normalizeMoney(
                        amountPaid
                );

        remainingBalance =
                normalizeMoney(
                        remainingBalance
                );
    }

    // ============================================================
    // PRE-UPDATE
    // ============================================================

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();

        installmentAmount =
                normalizeMoney(
                        installmentAmount
                );

        principalAmount =
                normalizeMoney(
                        principalAmount
                );

        interestAmount =
                normalizeMoney(
                        interestAmount
                );

        penaltyAmount =
                normalizeMoney(
                        penaltyAmount
                );

        amountPaid =
                normalizeMoney(
                        amountPaid
                );

        remainingBalance =
                normalizeMoney(
                        remainingBalance
                );
    }

    // ============================================================
    // MONEY NORMALIZATION
    // ============================================================

    private BigDecimal normalizeMoney(
            BigDecimal value
    ) {

        if (value == null) {
            return ZERO;
        }

        return value.setScale(
                MONEY_SCALE,
                java.math.RoundingMode.HALF_UP
        );
    }

    // ============================================================
    // STATUS
    // ============================================================

    public enum ScheduleStatus {

        PENDING,

        PARTIAL,

        PAID,

        OVERDUE
    }
}
