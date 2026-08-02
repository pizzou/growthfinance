
package com.patrick.fintech.loan_backend.util;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentUserUtil {

    private final UserRepository userRepository;

    /**
     * Returns the currently authenticated application user.
     */
    public User getCurrentUser() {

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            throw new IllegalStateException("No authenticated user");
        }

        if (!auth.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated");
        }

        String email = auth.getName();

        if (email == null || email.isBlank()) {
            throw new IllegalStateException(
                    "Authenticated user email is missing"
            );
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Current user not found: " + email
                        )
                );
    }

    /**
     * Returns the organization belonging to the current user.
     *
     * This is the tenant boundary used by accounting,
     * loans, borrowers, banking and other organization-scoped APIs.
     */
    public Long getCurrentOrganizationId() {

        User user = getCurrentUser();

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

        return user.getOrganization().getId();
    }

    public Long getCurrentUserId() {

        User user = getCurrentUser();

        if (user.getId() == null) {
            throw new IllegalStateException(
                    "Current user has no ID"
            );
        }

        return user.getId();
    }
}
