package com.patrick.fintech.loan_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoreResponse {
    private double score;
    private String category;
    private String recommendation;
    private double repaymentFactor;
    private double creditFactor;
    private double ltvFactor;
    private double kycFactor;
    private double concentrationFactor;

    public RiskScoreResponse(double score, String category, String recommendation,
                              double repaymentFactor, double creditFactor,
                              double ltvFactor, double kycFactor) {
        this(score, category, recommendation, repaymentFactor, creditFactor, ltvFactor, kycFactor, 0.0);
    }
}