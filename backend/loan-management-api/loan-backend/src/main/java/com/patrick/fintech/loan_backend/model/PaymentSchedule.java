
package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("installmentAmount")
    private BigDecimal installmentAmount;

    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("principalAmount")
    private BigDecimal principalAmount;

    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("interestAmount")
    private BigDecimal interestAmount;

    @Builder.Default
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("penaltyAmount")
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("amountPaid")
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Builder.Default
    @Column(
            nullable = false,
            precision = 19,
            scale = 6
    )
    @JsonProperty("remainingBalance")
    private BigDecimal remainingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleStatus status;

    private LocalDate paidDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = ScheduleStatus.PENDING;
        }

        if (penaltyAmount == null) {
            penaltyAmount = BigDecimal.ZERO;
        }

        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }

        if (remainingBalance == null) {
            remainingBalance = BigDecimal.ZERO;
        }

        if (installmentAmount == null) {
            installmentAmount = BigDecimal.ZERO;
        }

        if (principalAmount == null) {
            principalAmount = BigDecimal.ZERO;
        }

        if (interestAmount == null) {
            interestAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (penaltyAmount == null) {
            penaltyAmount = BigDecimal.ZERO;
        }

        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }

        if (remainingBalance == null) {
            remainingBalance = BigDecimal.ZERO;
        }

        if (installmentAmount == null) {
            installmentAmount = BigDecimal.ZERO;
        }

        if (principalAmount == null) {
            principalAmount = BigDecimal.ZERO;
        }

        if (interestAmount == null) {
            interestAmount = BigDecimal.ZERO;
        }
    }

    public enum ScheduleStatus {
        PENDING,
        PAID,
        PARTIAL,
        OVERDUE
    }
}
