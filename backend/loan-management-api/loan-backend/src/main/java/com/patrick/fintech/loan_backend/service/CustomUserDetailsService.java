package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Role.getName() now returns String (e.g. "ADMIN", "LOAN_OFFICER")
        String roleName = user.getRole() != null ? user.getRole().getName() : "BORROWER";

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.getStatus() == User.UserStatus.ACTIVE,
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))
        );
    }
}
