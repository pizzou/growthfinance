package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "esignature_requests",
    indexes = {
        @Index(name = "idx_esign_loan", columnList = "loan_id"),
        @Index(name = "idx_esign_token", columnList = "signing_token")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ESignatureRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "signing_token", unique = true, nullable = false)
    private String signingToken;

    private String documentType;      // LOAN_AGREEMENT, DISBURSEMENT_ACKNOWLEDGEMENT, RESTRUCTURE_ADDENDUM

    @Enumerated(EnumType.STRING)
    private SignatureStatus status;

    @JsonIgnore
    private String otpCodeHash;
    private Integer otpAttempts;
    private LocalDateTime otpSentAt;

    @Column(columnDefinition = "TEXT")
    private String documentSnapshot;    // rendered agreement text at time of sending, for integrity
    private String documentHash;        // SHA-256 of documentSnapshot

    @Column(columnDefinition = "TEXT")
    private String consentText;

    private String signerFullNameTyped;
    private String signerIpAddress;
    private String signerUserAgent;
    private String declineReason;

    private String createdBy;

    private LocalDateTime sentAt;
    private LocalDateTime signedAt;
    private LocalDateTime declinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = SignatureStatus.PENDING;
        if (otpAttempts == null) otpAttempts = 0;
        if (expiresAt == null) expiresAt = createdAt.plusDays(7);
    }

    public boolean isExpired() { return expiresAt != null && expiresAt.isBefore(LocalDateTime.now()); }

    public enum SignatureStatus { PENDING, OTP_SENT, SIGNED, DECLINED, EXPIRED }
}
