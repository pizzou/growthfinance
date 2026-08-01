
package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "payment_schedules",
        indexes = {
                @Index(
                        name = "idx_payment_schedule_loan",
                        columnList = "loan_id"
                ),
                @Index(
                        name = "idx_payment_schedule_due_date",
                        columnList = "due_date"
                ),
                @Index(
                        name = "idx_payment_schedule_status",
                        columnList = "status"
                )
        }
)
@JsonIgnoreProperties({
        "hibernateLazyInitializer",
        "handler"
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    // ============================================================
    // ID
    // ============================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // ============================================================
    // LOAN
    // ============================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "loan_id",
            nullable = false
    )
    private Loan loan;


    // ============================================================
    // INSTALLMENT
    // ============================================================

    @Column(
            name = "installment_number",
            nullable = false
    )
    private Integer installmentNumber;


    @Column(
            name = "due_date",
            nullable = false
    )
    private LocalDate dueDate;


    // ============================================================
    // FINANCIAL VALUES
    // ============================================================

    /**
     * Total amount due for this installment.
     */
    @Column(
            name = "installment_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal installmentAmount = BigDecimal.ZERO;


    /**
     * Principal component of this installment.
     */
    @Column(
            name = "principal_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal principalAmount = BigDecimal.ZERO;


    /**
     * Interest component of this installment.
     */
    @Column(
            name = "interest_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal interestAmount = BigDecimal.ZERO;


    /**
     * Penalty applicable to this installment.
     */
    @Column(
            name = "penalty_amount",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;


    /**
     * Amount already paid toward this installment.
     */
    @Column(
            name = "amount_paid",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;


    @Column(
            name = "remaining_balance",
            precision = 19,
            scale = 2,
            nullable = false
    )
    @Builder.Default
    private BigDecimal remainingBalance = BigDecimal.ZERO;


 

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    @Builder.Default
    private ScheduleStatus status =
            ScheduleStatus.PENDING;


   

    @PrePersist
    protected void onCreate() {

        normalizeMoney();

        if (status == null) {
            status = ScheduleStatus.PENDING;
        }
    }


    @PreUpdate
    protected void onUpdate() {

        normalizeMoney();
    }

    private void normalizeMoney() {

        installmentAmount =
                normalize(installmentAmount);

        principalAmount =
                normalize(principalAmount);

        interestAmount =
                normalize(interestAmount);

        penaltyAmount =
                normalize(penaltyAmount);

        amountPaid =
                normalize(amountPaid);

        remainingBalance =
                normalize(remainingBalance);
    }


    private BigDecimal normalize(BigDecimal value) {

        if (value == null) {
            return BigDecimal.ZERO.setScale(
                    2
            );
        }

        return value.setScale(
                2,
                java.math.RoundingMode.HALF_UP
        );
    }


    public Double getInstallmentAmountDouble() {

        return installmentAmount == null
                ? null
                : installmentAmount.doubleValue();
    }


    public Double getPrincipalAmountDouble() {

        return principalAmount == null
                ? null
                : principalAmount.doubleValue();
    }


    public Double getInterestAmountDouble() {

        return interestAmount == null
                ? null
                : interestAmount.doubleValue();
    }


    public Double getPenaltyAmountDouble() {

        return penaltyAmount == null
                ? null
                : penaltyAmount.doubleValue();
    }


    public Double getAmountPaidDouble() {

        return amountPaid == null
                ? null
                : amountPaid.doubleValue();
    }


    public Double getRemainingBalanceDouble() {

        return remainingBalance == null
                ? null
                : remainingBalance.doubleValue();
    }


 

    public enum ScheduleStatus {

        PENDING,

        PARTIALLY_PAID,

        PAID,

        OVERDUE,

        DEFAULTED,

        WAIVED,

        CANCELLED
    }
}
