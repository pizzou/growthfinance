package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    // Maps to expiry_date column in V1 migration
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiresAt;

    private boolean used = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
