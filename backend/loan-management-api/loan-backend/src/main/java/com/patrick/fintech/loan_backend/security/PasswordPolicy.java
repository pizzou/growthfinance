package com.patrick.fintech.loan_backend.security;

import java.util.regex.Pattern;

/**
 * Bank-grade password complexity policy, enforced everywhere a password is
 * ever set (registration, admin-created users, self-service reset, admin
 * reset) — see AuthService, UserService, PasswordResetService.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    private static final int MIN_LENGTH = 10;
    private static final Pattern UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern LOWER   = Pattern.compile("[a-z]");
    private static final Pattern DIGIT   = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    /** Throws with a clear, specific message if the password doesn't meet policy. Returns silently if it does. */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH)
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters long");
        if (!UPPER.matcher(password).find())
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        if (!LOWER.matcher(password).find())
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        if (!DIGIT.matcher(password).find())
            throw new IllegalArgumentException("Password must contain at least one digit");
        if (!SPECIAL.matcher(password).find())
            throw new IllegalArgumentException("Password must contain at least one special character (e.g. !@#$%)");
    }
}
