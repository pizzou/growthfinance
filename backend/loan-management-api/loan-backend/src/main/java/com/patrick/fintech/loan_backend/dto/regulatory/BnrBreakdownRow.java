package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One row of a breakdown table — loan type, branch, PAR bucket, etc. */
@Data @AllArgsConstructor
public class BnrBreakdownRow {
    private String label;   // e.g. "PERSONAL", "Kigali Main Branch", "31-60 Days"
    private long   count;
    private double amount;
}