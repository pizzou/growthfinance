package com.patrick.fintech.loan_backend.model;

import java.math.BigDecimal;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "borrowers",
    indexes = {
        @Index(name = "idx_borrower_org", columnList = "organization_id"),
        @Index(name = "idx_borrower_email", columnList = "email")
    })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Borrower {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String firstName;

    @NotBlank(message = "Last name is required")
@Column(nullable = false)
    private String lastName;
    @NotBlank(message = "Email is required")
@Email(message = "Invalid email address")
@Column(nullable = false, unique = true)
    private String email;

    /** Encrypted at rest — see CryptoConverter. Exact-match lookups use phoneHash, not this column. */
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String phone;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String alternatePhone;

    /** Deterministic HMAC of phone, kept in sync via @PrePersist/@PreUpdate — query by this, not phone directly. */
    private String phoneHash;

    @NotBlank(message = "National ID is required")
@Pattern(
    regexp = "^\\d{16}$",
    message = "National ID must contain exactly 16 digits"
)
@Column(nullable = false, unique = true)
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String nationalId;
    /** Deterministic HMAC of nationalId — query/duplicate-check by this, not nationalId directly. */
    private String nationalIdHash;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String passportNumber;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String taxIdentificationNumber;

    private LocalDate dateOfBirth;
    @NotNull(message = "Gender is required")

@Column(nullable = false)
    private String    gender;

    @Column(nullable = false)
    private String    maritalStatus;

    // ---- Marital status documentation (required in Rwanda for loans involving
    // shared/community property — single status must be certified, and a
    // spouse's details are required for married applicants). ----
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String    singleCertificateNumber;   // "Célibat"/Single Status Certificate reference, if single
    private String    spouseFullName;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String    spouseNationalId;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String    spousePhone;
    private Boolean   spouseConsent;              // spouse consents to the loan/collateral use
    private String    nationality;       // ISO-3166 alpha-2

      @Builder.Default
    private Boolean imported = false;
    private Long    importBatchId;
    // Address
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String address;       // convenience single-line address
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String addressLine1;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;

    // Employment
    private String employerName;
    private String employmentType;       // PERMANENT, CONTRACT, SELF_EMPLOYED, UNEMPLOYED
    private String jobTitle;
    @Column(precision = 19, scale = 6)
    @JsonProperty("monthlyIncome")
    private BigDecimal monthlyIncome;
    @JsonProperty("monthlyExpenses")
    private BigDecimal monthlyExpenses;
    @JsonProperty("netWorth")
    @Column(precision = 19, scale = 6)
    private BigDecimal netWorth;

    // Credit
    private Integer creditScore;
    private String  creditBureau;
    private LocalDate creditReportDate;

    private String kycStatus;  // PENDING, VERIFIED, REJECTED

   @Enumerated(EnumType.STRING)
    private BorrowerStatus status;

    private String blacklistReason;
    private LocalDateTime blacklistedAt;

    @ManyToOne(fetch = FetchType.LAZY)

@JoinColumn(name = "blacklisted_by")

private User blacklistedBy;

    private String  bankName;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String  bankAccountNumber;
    private String  bankBranch;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now();
        if (status == null) status = BorrowerStatus.ACTIVE;
        if (kycStatus == null) kycStatus = "PENDING";
        phoneHash = com.patrick.fintech.loan_backend.security.HmacIndexer.index(phone);
        nationalIdHash = com.patrick.fintech.loan_backend.security.HmacIndexer.index(nationalId);
    }

    @PreUpdate protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        phoneHash = com.patrick.fintech.loan_backend.security.HmacIndexer.index(phone);
        nationalIdHash = com.patrick.fintech.loan_backend.security.HmacIndexer.index(nationalId);
    }

    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public enum BorrowerStatus { ACTIVE, INACTIVE, BLACKLISTED, DECEASED }
    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMonthlyIncomeDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMonthlyIncome() {
        return monthlyIncome == null ? null : monthlyIncome.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyIncomeDecimal() {
        return monthlyIncome;
    }

    @Deprecated
    public void setMonthlyIncome(Double value) {
        this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyIncome(BigDecimal value) {
        this.monthlyIncome = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getMonthlyExpensesDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getMonthlyExpenses() {
        return monthlyExpenses == null ? null : monthlyExpenses.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getMonthlyExpensesDecimal() {
        return monthlyExpenses;
    }

    @Deprecated
    public void setMonthlyExpenses(Double value) {
        this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setMonthlyExpenses(BigDecimal value) {
        this.monthlyExpenses = value;
    }


    /**
     * Legacy binary-floating-point read boundary retained for existing service integrations.
     * New financial code should use getNetWorthDecimal().
     */
    @Deprecated
    @JsonIgnore
    public Double getNetWorth() {
        return netWorth == null ? null : netWorth.doubleValue();
    }

    @JsonIgnore
    public BigDecimal getNetWorthDecimal() {
        return netWorth;
    }

    @Deprecated
    public void setNetWorth(Double value) {
        this.netWorth = value == null ? null : BigDecimal.valueOf(value);
    }

    public void setNetWorth(BigDecimal value) {
        this.netWorth = value;
    }

    /** Backward-compatible builder overloads for legacy Double callers.
     *  Financial state is stored as BigDecimal.
     */
    public static class BorrowerBuilder {
        private BigDecimal monthlyExpenses;
        private BigDecimal monthlyIncome;
        private BigDecimal netWorth;


        public BorrowerBuilder monthlyIncome(Double value) {
            this.monthlyIncome = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerBuilder monthlyExpenses(Double value) {
            this.monthlyExpenses = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }
        public BorrowerBuilder netWorth(Double value) {
            this.netWorth = value == null ? null : BigDecimal.valueOf(value);
            return this;
        }        public BorrowerBuilder monthlyIncome(BigDecimal value) {
            this.monthlyIncome = value;
            return this;
        }
        public BorrowerBuilder monthlyExpenses(BigDecimal value) {
            this.monthlyExpenses = value;
            return this;
        }
        public BorrowerBuilder netWorth(BigDecimal value) {
            this.netWorth = value;
            return this;
        }
    }

}
