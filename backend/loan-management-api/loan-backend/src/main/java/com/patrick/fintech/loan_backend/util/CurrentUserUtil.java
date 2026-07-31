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

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new RuntimeException("Current user not found"));
    }

    public Long getCurrentOrganizationId() {
        return getCurrentUser().getOrganization().getId();
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}