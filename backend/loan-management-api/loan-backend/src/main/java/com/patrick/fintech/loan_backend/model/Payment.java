package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "payments",
    indexes = {
        @Index(name = "idx_payment_loan", columnList = "loan_id"),
        @Index(name = "idx_payment_due", columnList = "due_date"),
        @Index(name = "idx_payment_org", columnList = "organization_id")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String paymentReference;   // e.g. PAY-20240101-000456

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    private Integer installmentNumber;
    private Double  amount;            // scheduled amount
    private Double  principalComponent;
    private Double  interestComponent;
    private Double  amountPaid;        // actual paid
    private Double  penalty;
    private Double  waivedAmount;
    private Double  outstandingAfter;

    private Boolean paid;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private String paymentMethod;      // MOBILE_MONEY, BANK_TRANSFER, CASH, CARD, GATEWAY
    private String transactionId;
    private String externalReference;
    private String gatewayResponse;
    private String channel;
    private String notes;
    private boolean isLate;
    private Integer daysLate;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (paid == null) paid = false;
        if (penalty == null) penalty = 0.0;
        if (waivedAmount == null) waivedAmount = 0.0;
        if (daysLate == null) daysLate = 0;
        if (status == null) status = PaymentStatus.PENDING;
    }

    public enum PaymentStatus { PENDING, COMPLETED, FAILED, REVERSED, PARTIALLY_PAID }
}
