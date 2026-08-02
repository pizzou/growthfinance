
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


    // ============================================================
    // CURRENT USER
    // ============================================================

    public User getCurrentUser() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();


        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {

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
    // ORGANIZATION
    // ============================================================

    public Long getCurrentOrganizationId() {

        User user =
                getCurrentUser();


        if (user.getOrganization() == null
                || user.getOrganization().getId() == null) {

            throw new IllegalStateException(
                    "Current user is not assigned to an organization"
            );
        }


        return user
                .getOrganization()
                .getId();
    }


    // ============================================================
    // USER ID
    // ============================================================

    public Long getCurrentUserId() {

        User user =
                getCurrentUser();


        if (user.getId() == null) {

            throw new IllegalStateException(
                    "Current user has no ID"
            );
        }


        return user.getId();
    }
}
