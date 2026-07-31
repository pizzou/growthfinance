package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.PasswordResetToken;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.PasswordResetTokenRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository              userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final MailService                mailService;
    private final AuditService                auditService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteByUser_Id(user.getId());
            PasswordResetToken t = new PasswordResetToken();
            t.setToken(UUID.randomUUID().toString());
            t.setUser(user);
            t.setExpiresAt(LocalDateTime.now().plusHours(1));
            tokenRepository.save(t);
            String link = frontendUrl + "/reset-password?token=" + t.getToken();
            mailService.sendPasswordResetEmail(user, link);
            auditService.log(user.getOrganization(), user, "PASSWORD_RESET_REQUESTED", "AUTH",
                String.valueOf(user.getId()), "Password reset requested for " + user.getEmail(),
                null, null, "Authentication");
            log.info("Password reset token generated for user={}", email);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken t = tokenRepository.findByTokenAndUsedFalse(token)
            .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));
        if (t.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new RuntimeException("Reset token has expired");
        User user = t.getUser();
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        t.setUsed(true);
        tokenRepository.save(t);
        auditService.log(user.getOrganization(), user, "PASSWORD_RESET_COMPLETED", "AUTH",
            String.valueOf(user.getId()), "Password reset completed for " + user.getEmail(),
            null, null, "Authentication");
        log.info("Password reset successfully for user={}", user.getEmail());
    }
}