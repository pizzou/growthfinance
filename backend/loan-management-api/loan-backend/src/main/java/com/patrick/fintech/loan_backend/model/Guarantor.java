package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "guarantors", indexes = @Index(name = "idx_guarantor_loan", columnList = "loan_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Guarantor {

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

    private String fullName;

    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String nationalId;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String phone;
    @Convert(converter = com.patrick.fintech.loan_backend.security.CryptoConverter.class)
    private String address;

    private String relationship;      // e.g. Spouse, Sibling, Colleague, Friend
    private String employerName;
    private Double monthlyIncome;

    private Double guaranteedAmount;  // how much of the loan this guarantor is on the hook for
    private Boolean consentGiven;
    private String documentUrl;       // scanned signed guarantee form, if uploaded

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (consentGiven == null) consentGiven = false;
    }
}
