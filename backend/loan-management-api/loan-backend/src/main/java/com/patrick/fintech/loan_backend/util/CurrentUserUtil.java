package com.patrick.fintech.loan_backend.util;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CurrentUserUtil {

    private final UserRepository userRepository;


    // ============================================================
    // CURRENT USER
    // ============================================================

    @Transactional(readOnly = true)
    public User getCurrentUser() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()
                || "anonymousUser".equals(auth.getName())) {

            throw new IllegalStateException(
                    "No authenticated user found"
            );
        }

        return userRepository
                .findByEmail(auth.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found: "
                                        + auth.getName()
                        )
                );
    }


    // ============================================================
    // ORGANIZATION ID
    // ============================================================

    @Transactional(readOnly = true)
    public Long getCurrentOrganizationId() {

        User user = getCurrentUser();

        if (user == null) {

            throw new IllegalStateException(
                    "Current user not found"
            );
        }

        if (user.getOrganization() == null) {

            throw new IllegalStateException(
                    "Current user is not assigned to an organization"
            );
        }

        if (user.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Current user's organization has no ID"
            );
        }

        return user
                .getOrganization()
                .getId();
    }


    // ============================================================
    // CURRENT USER ID
    // ============================================================

    @Transactional(readOnly = true)
    public Long getCurrentUserId() {

        User user = getCurrentUser();

        if (user == null || user.getId() == null) {

            throw new IllegalStateException(
                    "Current user has no ID"
            );
        }

        return user.getId();
    }
}
