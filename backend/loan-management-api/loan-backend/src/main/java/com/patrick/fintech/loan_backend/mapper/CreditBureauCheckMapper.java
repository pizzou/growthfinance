package com.patrick.fintech.loan_backend.mapper;

import com.patrick.fintech.loan_backend.dto.CreditBureauCheckResponse;
import com.patrick.fintech.loan_backend.model.CreditBureauCheck;

import org.springframework.stereotype.Component;

@Component
public class CreditBureauCheckMapper {

    public CreditBureauCheckResponse toResponse(
            CreditBureauCheck check
    ) {

        if (check == null) {
            return null;
        }

        return CreditBureauCheckResponse.builder()

                // ====================================================
                // BASIC INFORMATION
                // ====================================================

                .id(
                        check.getId()
                )

                .reference(
                        check.getReference()
                )

                .provider(
                        check.getProvider()
                )

                // IMPORTANT:
                // DTO expects CreditBureauCheck.CheckStatus,
                // therefore do NOT use .name()
                .status(
                        check.getStatus()
                )

                // ====================================================
                // IDENTIFICATION
                // ====================================================

                .nationalIdChecked(
                        check.getNationalIdChecked()
                )

                // ====================================================
                // CREDIT SCORE
                // ====================================================

                .creditScore(
                        check.getCreditScore()
                )

                .riskGrade(
                        check.getRiskGrade()
                )

                // ====================================================
                // CREDIT FACILITIES
                // ====================================================

                .activeFacilities(
                        check.getActiveFacilities()
                )

                .delinquentAccounts(
                        check.getDelinquentAccounts()
                )

                // ====================================================
                // DEBT
                // ====================================================

                .totalOutstandingDebt(
                        check.getTotalOutstandingDebtDecimal()
                )

                .totalMonthlyObligations(
                        check.getTotalMonthlyObligationsDecimal()
                )

                // ====================================================
                // DEFAULT / LISTING
                // ====================================================

                .hasDefaultHistory(
                        check.getHasDefaultHistory()
                )

                .hasActiveListing(
                        check.getHasActiveListing()
                )

                .listingReason(
                        check.getListingReason()
                )

                // ====================================================
                // REQUEST INFORMATION
                // ====================================================

                .requestedBy(
                        check.getRequestedBy()
                )

                .failureReason(
                        check.getFailureReason()
                )

                // ====================================================
                // DATES
                // ====================================================

                .createdAt(
                        check.getCreatedAt()
                )

                .expiresAt(
                        check.getExpiresAt()
                )

                .build();
    }
}
