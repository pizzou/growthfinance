package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.patrick.fintech.loan_backend.util.MoneyMath;

@Entity
@Table(name="payment_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name="uq_payment_txn_reference", columnNames={"organization_id","transaction_reference"})
    },
    indexes = {
        @Index(name="idx_payment_tx_loan", columnList="loan_id"),
        @Index(name="idx_payment_tx_installment", columnList="installment_id"),
        @Index(name="idx_payment_tx_status", columnList="status")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="loan_id", nullable=false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="organization_id", nullable=false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="installment_id")
    private Payment installment;

    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="recorded_by")
    private User recordedBy;

    @Column(name="transaction_reference", nullable=false, length=120)
    private String transactionReference;

    @Column(nullable=false, precision=19, scale=2)
    private BigDecimal amount;

    @Column(precision=19, scale=2)
    private BigDecimal penaltyComponent;

    @Column(precision=19, scale=2)
    private BigDecimal interestComponent;

    @Column(precision=19, scale=2)
    private BigDecimal principalComponent;

    @Column(precision=19, scale=2)
    private BigDecimal unappliedAmount;

    private String paymentMethod;
    private String channel;
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.POSTED;

    @Column(nullable=false)
    @Builder.Default
    private Boolean reversed = false;

    private LocalDateTime createdAt;
    private LocalDateTime reversedAt;

    @Column(length=500)
    private String reversalReason;

    @Column(length=120)
    private String reversalReference;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TransactionStatus.POSTED;
        if (reversed == null) reversed = false;
        if (penaltyComponent == null) penaltyComponent = MoneyMath.ZERO;
        if (interestComponent == null) interestComponent = MoneyMath.ZERO;
        if (principalComponent == null) principalComponent = MoneyMath.ZERO;
        if (unappliedAmount == null) unappliedAmount = MoneyMath.ZERO;
    }

    public enum TransactionStatus { POSTED, REVERSED }
}