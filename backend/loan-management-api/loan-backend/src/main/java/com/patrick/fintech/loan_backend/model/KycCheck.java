package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="kyc_checks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class KycCheck {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) 
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="borrower_id",nullable=false) 
    private Borrower borrower;


    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="organization_id",nullable=false) private Organization organization;
    @Enumerated(EnumType.STRING) private CheckType checkType;
    @Enumerated(EnumType.STRING) private CheckResult result;
    @Column(precision = 19, scale = 9)
    @JsonProperty("matchScore")
    private BigDecimal matchScore;
    @Column(columnDefinition="TEXT") private String rawResponse;
    private String provider;
    private String notes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    @PrePersist protected void onCreate() {
        createdAt=LocalDateTime.now();
        if(expiresAt==null) expiresAt=createdAt.plusMonths(12);
        if(result==null) result=CheckResult.PENDING;
    }
    public boolean isExpired(){ return expiresAt!=null&&expiresAt.isBefore(LocalDateTime.now()); }
    public enum CheckType  { IDENTITY_VERIFICATION,SANCTIONS_SCREENING,PEP_SCREENING,ADDRESS_VERIFICATION,DOCUMENT_VERIFICATION }
    public enum CheckResult{ PENDING,CLEAR,FLAGGED,REJECTED,MANUAL_REVIEW }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMatchScoreDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMatchScore() {
        return matchScore == null ? null : matchScore.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMatchScoreDecimal() {
        return matchScore;
    }

    @Deprecated
    public void setMatchScore(Double value) {
        this.matchScore = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMatchScore(BigDecimal value) {
        this.matchScore = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class KycCheckBuilder {
        private BigDecimal matchScore;


        public KycCheckBuilder matchScore(Double value) {
            this.matchScore = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public KycCheckBuilder matchScore(BigDecimal value) {
            this.matchScore = value;
            return this;
        }
    }

}
