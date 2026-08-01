package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Persisted, immutable copy of one CreditBureauRecord for one loan in one reporting period.
 *  Same field shape as the existing CreditBureauRecord DTO — this is that DTO, snapshotted.
 *  Never edited once its reportingStatus moves past PENDING; see
 *  RegulatoryReportingService#persistSubmission for the correction-chain logic. */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "credit_bureau_submission_records",
    indexes = {
        @Index(name = "idx_cbsr_org_period", columnList = "organization_id, reporting_period"),
        @Index(name = "idx_cbsr_loan", columnList = "loan_id"),
        @Index(name = "idx_cbsr_borrower", columnList = "borrower_id")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreditBureauSubmissionRecord {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CreditBureauSubmission submission;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Borrower borrower;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Loan loan;

    @Column(name = "reporting_period", nullable = false, length = 7)
    private String reportingPeriod;

    // ---- Same fields as CreditBureauRecord DTO ----
    private String fullName;
    private String nationalId;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;
    private String loanNumber;
    private String loanType;
    private String loanStatus;
    private Double loanAmount;
    private Double outstandingBalance;
    private Integer daysPastDue;
    private Integer creditScore;
    private LocalDate dateOpened;
    private LocalDate lastPaymentDate;
    private LocalDate maturityDate;
    private LocalDate dateClosed;
    private String branchName;
    private String currency;

    // ---- New regulatory fields, from Loan.creditQuality/arrearsStatus ----
    private String classification;
    private String repaymentStatus;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "reporting_status", nullable = false, length = 20)
    private ReportingStatus reportingStatus = ReportingStatus.PENDING;

    @Column(name = "correction_of_record_id")
    private Long correctionOfRecordId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (reportingStatus == null) reportingStatus = ReportingStatus.PENDING;
    }

    public enum ReportingStatus { PENDING, VALIDATED, SUBMITTED, ACCEPTED, REJECTED, CORRECTED }
}