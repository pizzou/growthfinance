package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One step in a loan's maker-checker approval chain. For loans above an
 * org's configured threshold, more than one distinct person — in distinct
 * roles — must sign off before the loan can actually be approved; nobody
 * can approve their own step twice, and the person who created/requested
 * the loan can never also be the one approving it. See LoanApprovalService.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "loan_approvals")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanApproval {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private Integer stepOrder;         // 1, 2, 3... — must be decided in order
    private String  requiredRole;      // which role must decide this step, e.g. "MANAGER"
    private String  stepName;          // human label, e.g. "Branch Manager Review", "Credit Committee"

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;             // who actually decided it (null while pending)

    private String status;             // PENDING, APPROVED, REJECTED
    private String comments;

    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
