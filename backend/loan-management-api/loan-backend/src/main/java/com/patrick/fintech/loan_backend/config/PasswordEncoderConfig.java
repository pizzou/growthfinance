package com.patrick.fintech.loan_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out of SecurityConfig on purpose. SecurityConfig constructor-injects
 * RegulatoryApiKeyAuthFilter (to register it in the filter chain), and that filter
 * constructor-injects PasswordEncoder — if PasswordEncoder were still a @Bean method
 * living inside SecurityConfig itself, building it would require the SecurityConfig
 * bean to already exist, which requires RegulatoryApiKeyAuthFilter to already exist,
 * which requires PasswordEncoder... a genuine cycle Spring refuses to resolve.
 * Keeping this bean in its own tiny, dependency-free config avoids that entirely.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}