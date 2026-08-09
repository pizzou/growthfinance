package com.patrick.fintech.loan_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportRowResult {
    private int rowNumber;
    private boolean success;
    /** CREATED_NEW_BORROWER, MATCHED_EXISTING_BORROWER — only meaningful on success. */
    private String borrowerAction;
    private String borrowerName;
    private String loanReferenceNumber;
    private String error;
}
