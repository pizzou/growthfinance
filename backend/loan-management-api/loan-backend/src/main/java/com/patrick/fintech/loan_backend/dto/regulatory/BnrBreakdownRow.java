package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One row of a regulatory breakdown.
 *
 * Examples:
 * PERSONAL
 * BUSINESS
 * Kigali Main Branch
 * MALE
 * FEMALE
 * 31-60 DAYS
 */
@Data
@Builder
@AllArgsConstructor
public class BnrBreakdownRow {

    private String label;

    private long count;

    private Double amount;
}