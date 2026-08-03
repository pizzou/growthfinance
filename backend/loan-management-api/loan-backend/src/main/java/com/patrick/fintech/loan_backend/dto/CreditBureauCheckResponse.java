package com.patrick.fintech.loan_backend.dto;

import com.patrick.fintech.loan_backend.model.CreditBureauCheck;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBureauCheckResponse {


    private Long id;

    private String reference;

    private String provider;

    private CreditBureauCheck.CheckStatus status;


   
    // private Long borrowerId;
    // private String borrowerName;

    private String nationalIdChecked;


    private Integer creditScore;

    private String riskGrade;


    private Integer activeFacilities;

    private Integer delinquentAccounts;

    private Double totalOutstandingDebt;

    private Double totalMonthlyObligations;


    private Boolean hasDefaultHistory;

    private Boolean hasActiveListing;

    private String listingReason;


    private String requestedBy;

    private String failureReason;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;


    private Boolean valid;

    private Boolean expired;
}