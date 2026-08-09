
package com.patrick.fintech.loan_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoreResponse {

    @JsonProperty("score")
    private BigDecimal score;

    private String category;

    private String recommendation;

    @JsonProperty("repaymentFactor")
    private BigDecimal repaymentFactor;

    @JsonProperty("creditFactor")
    private BigDecimal creditFactor;

    @JsonProperty("ltvFactor")
    private BigDecimal ltvFactor;

    @JsonProperty("kycFactor")
    private BigDecimal kycFactor;

    @JsonProperty("concentrationFactor")
    private BigDecimal concentrationFactor;

   
    public RiskScoreResponse(
            BigDecimal score,
            String category,
            String recommendation,
            BigDecimal repaymentFactor,
            BigDecimal creditFactor,
            BigDecimal ltvFactor,
            BigDecimal kycFactor) {

        this(
                score,
                category,
                recommendation,
                repaymentFactor,
                creditFactor,
                ltvFactor,
                kycFactor,
                BigDecimal.ZERO
        );
    }
}
