package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "organizations")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Organization {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;
    private String industry;
    private String country;
    private String defaultCurrency;
    private String timezone;
    private String locale;
    private String logoUrl;
    private String primaryColor;
    private String accentColor;
    private String website;
    private String contactEmail;
    private String contactPhone;
    private String address;
    private String registrationNumber;

    // ---- Public marketing website content ----
    private String tagline;
    @Column(columnDefinition = "TEXT")
    private String mission;
    @Column(columnDefinition = "TEXT")
    private String vision;
    private Integer foundedYear;
    private String facebookUrl;
    private String instagramUrl;
    private String linkedinUrl;
    private String twitterUrl;
    private String whatsappUrl;
    @Column(columnDefinition = "TEXT")
    private String mapUrl;

    // ---- Flexible CMS content (stored as JSON, parsed/defaulted by PublicController) ----
    private String heroHeadline;
    @Column(columnDefinition = "TEXT")
    private String heroSubtext;
    @Column(columnDefinition = "TEXT")
    private String statsJson;         // [{icon,value,label}]
    @Column(columnDefinition = "TEXT")
    private String servicesJson;      // [{title,icon,rate,maxAmount,term,description}]
    @Column(columnDefinition = "TEXT")
    private String testimonialsJson;  // [{name,role,text,rating}]
    @Column(columnDefinition = "TEXT")
    private String teamJson;          // [{name,role,initials}]

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTier subscriptionTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, precision = 19, scale = 6)
    private OrgStatus status;

    private Integer maxUsers;
    private Integer maxActiveLoans;
    @JsonProperty("maxLoanAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal maxLoanAmount;
    @JsonProperty("minLoanAmount")
    @Column(precision = 19, scale = 6)
    private BigDecimal minLoanAmount;

    private String stripeCustomerId;
    private LocalDateTime subscribedAt;
    private LocalDateTime trialEndsAt;
    private LocalDateTime subscriptionExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    private List<User> users;

    @JsonIgnore
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    private List<Loan> loans;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (subscriptionTier == null) subscriptionTier = SubscriptionTier.TRIAL;
        if (status == null)           status = OrgStatus.ACTIVE;
        if (defaultCurrency == null)  defaultCurrency = "USD";
        if (timezone == null)         timezone = "UTC";
        if (locale == null)           locale = "en-US";
        if (maxUsers == null)         maxUsers = 100;
        if (maxActiveLoans == null)   maxActiveLoans = 10000;
        if (minLoanAmount == null)    minLoanAmount = BigDecimal.valueOf(100.0);
        if (maxLoanAmount == null)    maxLoanAmount = BigDecimal.valueOf(1000000.0);
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum SubscriptionTier { TRIAL, STARTER, PROFESSIONAL, ENTERPRISE, UNLIMITED }
    public enum OrgStatus        { ACTIVE, SUSPENDED, TRIAL, EXPIRED, PENDING_SETUP }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMaxLoanAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMaxLoanAmount() {
        return maxLoanAmount == null ? null : maxLoanAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMaxLoanAmountDecimal() {
        return maxLoanAmount;
    }

    @Deprecated
    public void setMaxLoanAmount(Double value) {
        this.maxLoanAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMaxLoanAmount(BigDecimal value) {
        this.maxLoanAmount = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMinLoanAmountDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMinLoanAmount() {
        return minLoanAmount == null ? null : minLoanAmount.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMinLoanAmountDecimal() {
        return minLoanAmount;
    }

    @Deprecated
    public void setMinLoanAmount(Double value) {
        this.minLoanAmount = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMinLoanAmount(BigDecimal value) {
        this.minLoanAmount = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class OrganizationBuilder {
        private BigDecimal maxLoanAmount;
        private BigDecimal minLoanAmount;


        public OrganizationBuilder maxLoanAmount(Double value) {
            this.maxLoanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public OrganizationBuilder minLoanAmount(Double value) {
            this.minLoanAmount = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public OrganizationBuilder maxLoanAmount(BigDecimal value) {
            this.maxLoanAmount = value;
            return this;
        }
        public OrganizationBuilder minLoanAmount(BigDecimal value) {
            this.minLoanAmount = value;
            return this;
        }
    }

}
