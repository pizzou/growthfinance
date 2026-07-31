package com.patrick.fintech.loan_backend.exception;

import com.patrick.fintech.loan_backend.model.Borrower;
import lombok.Getter;

/**
 * Thrown when someone tries to create a borrower whose email, phone, or national ID already
 * matches an existing record in the same organization. Previously this situation just threw a
 * plain RuntimeException and blocked — with no way to tell staff "this is the same person, here's
 * their existing profile" instead of a dead-end error. This carries the existing Borrower so
 * GlobalExceptionHandler can return it, and the frontend can offer "View existing borrower" /
 * "create a new loan for them" instead of forcing a duplicate or giving up.
 */
@Getter
public class DuplicateBorrowerException extends RuntimeException {
    private final Borrower existingBorrower;
    private final String matchedOn; // "email", "phone", or "national ID"

    public DuplicateBorrowerException(String message, Borrower existingBorrower, String matchedOn) {
        super(message);
        this.existingBorrower = existingBorrower;
        this.matchedOn = matchedOn;
    }
}
