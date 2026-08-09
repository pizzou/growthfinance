package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "app_users",
       indexes = @Index(name = "idx_users_email", columnList = "email"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    private String phone;
    private String avatarUrl;
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private boolean twoFactorEnabled;

    /** True right after an admin creates this account with a system-generated password —
     *  forces the user through a mandatory password change before they can use the rest
     *  of the app. Cleared automatically the first time they successfully set their own
     *  password (see UserService.changeOwnPassword). */
    @Builder.Default
    private boolean mustChangePassword = false;

    @JsonIgnore
    private String twoFactorSecret;

    private Integer failedLoginAttempts;
    private LocalDateTime lockedUntil;

    // Email OTP — a lighter-weight second factor used for roles that don't require
    // full TOTP enrollment (see AuthController.MFA_MANDATORY_ROLES). Hashed like a
    // password, never stored or logged in plaintext; single-use and short-lived.
    @JsonIgnore
    private String loginOtpHash;
    private LocalDateTime loginOtpExpiresAt;
    @Builder.Default
    private Integer loginOtpAttempts = 0;

    private LocalDateTime lastLoginAt;
    private String lastLoginIp;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<AuditLog> auditLogs;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = UserStatus.ACTIVE;
        if (failedLoginAttempts == null) failedLoginAttempts = 0;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }


    public String getFullName() {
    return name;
}
    public enum UserStatus { ACTIVE, INACTIVE, SUSPENDED, PENDING_INVITE }
}
