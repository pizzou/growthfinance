package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(nullable = false)
    private Integer installmentNumber;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private Double installmentAmount;

    @Column(nullable = false)
    private Double principalAmount;

    @Column(nullable =false)
    private Double interestAmount;

    @Builder.Default
    private Double penaltyAmount = 0.0;

    @Builder.Default
    private Double amountPaid = 0.0;

    @Builder.Default
    private Double remainingBalance = 0.0;

    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    private LocalDate paidDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        if(status==null){
            status = ScheduleStatus.PENDING;
        }

        if(amountPaid==null){
            amountPaid = 0.0;
        }

        if(penaltyAmount==null){
            penaltyAmount = 0.0;
        }

    }

    @PreUpdate
    public void onUpdate(){
        updatedAt = LocalDateTime.now();
    }

    public enum ScheduleStatus{
        PENDING,
        PAID,
        PARTIAL,
        OVERDUE
    }

}