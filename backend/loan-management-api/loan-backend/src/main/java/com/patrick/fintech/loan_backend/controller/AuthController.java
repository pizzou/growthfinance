package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.config.JwtUtils;
import com.patrick.fintech.loan_backend.dto.*;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.*;
import com.patrick.fintech.loan_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    @PostMapping("/register") @Transactional
    public ResponseEntity<Map<String,Object>> register(@RequestBody RegisterRequest req) {
        User created = authService.register(req);
        auditService.log(created.getOrganization(), created, "USER_REGISTERED", "AUTH",
            String.valueOf(created.getId()), created.getName() + " (" + created.getEmail() + ") registered",
            null, null, "Authentication");
        return ResponseEntity.ok(safe(created));
    }

    // Roles that can create/delete users, change loan products/rates, or approve large
    // transactions must have MFA enabled — not just offered. Extend this set as needed.
    private static final java.util.Set<String> MFA_MANDATORY_ROLES = java.util.Set.of("ADMIN", "MANAGER");

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @PostMapping("/login") @Transactional
    public ResponseEntity<Map<String,Object>> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail()).orElse(null);

        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(java.time.LocalDateTime.now())) {
            long minutesLeft = java.time.Duration.between(java.time.LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            auditService.log(user.getOrganization(), user, "LOGIN_BLOCKED_ACCOUNT_LOCKED", "AUTH",
                String.valueOf(user.getId()), "Login attempt rejected — account locked", null, null, "Authentication");
            throw new RuntimeException("Account locked due to repeated failed logins. Try again in " + minutesLeft + " minute(s).");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        } catch (Exception e) {
            if (user != null) {
                int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    user.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                    userRepository.save(user);
                    auditService.log(user.getOrganization(), user, "ACCOUNT_LOCKED", "AUTH",
                        String.valueOf(user.getId()), "Account locked after " + attempts + " failed login attempts",
                        null, null, "Authentication");
                    throw new RuntimeException("Too many failed attempts. Account locked for " + LOCKOUT_MINUTES + " minutes.");
                }
                userRepository.save(user);
                auditService.log(user.getOrganization(), user, "LOGIN_FAILED", "AUTH",
                    String.valueOf(user.getId()), "Failed login attempt (" + attempts + "/" + MAX_FAILED_ATTEMPTS + ")",
                    null, null, "Authentication");
            }
            throw new RuntimeException("Invalid email or password");
        }

        user = userRepository.findByEmail(req.getEmail()).orElseThrow(()->new RuntimeException("User not found"));
        // Successful password check — reset the failure counter and any lock
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0) || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        boolean mfaRequiredForRole = user.getRole() != null && MFA_MANDATORY_ROLES.contains(user.getRole().getName());

        if (!user.isTwoFactorEnabled() && mfaRequiredForRole) {
            // This role requires MFA but hasn't enrolled yet — issue a setup-only token
            // instead of a real session. There is no way to reach the rest of the API
            // without finishing enrollment (see JwtAuthFilter).
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("mfaSetupRequired", true);
            body.put("mfaRequired", false);
            body.put("email", user.getEmail());
            body.put("setupToken", jwtUtils.generateSetupToken(user.getEmail()));
            body.put("message", "Your role requires two-factor authentication. Complete setup to continue.");
            return ResponseEntity.ok(body);
        }

        if(user.isTwoFactorEnabled()) {
            if(req.getMfaCode()==null||req.getMfaCode().isBlank()) {
                return ResponseEntity.ok(Map.of("mfaRequired",true,"email",user.getEmail()));
            }
            if(!mfaService.verifyCode(user,req.getMfaCode())) throw new RuntimeException("Invalid MFA code");
        } else {
            // No TOTP app enrolled (and this role doesn't mandate one) — fall back to a
            // one-time code emailed at sign-in as a lighter-weight second factor. Every
            // login goes through this, not just the mandatory-MFA roles.
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            if (req.getOtp() == null || req.getOtp().isBlank()) {
                String code = String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
                user.setLoginOtpHash(passwordEncoder.encode(code));
                user.setLoginOtpExpiresAt(now.plusMinutes(5));
                user.setLoginOtpAttempts(0);
                userRepository.save(user);
                mailService.sendLoginOtp(user, code);
                return ResponseEntity.ok(Map.of(
                    "otpRequired", true, "email", user.getEmail(),
                    "message", "We sent a 6-digit verification code to your email."));
            }

            if (user.getLoginOtpHash() == null || user.getLoginOtpExpiresAt() == null || user.getLoginOtpExpiresAt().isBefore(now)) {
                throw new RuntimeException("Your verification code has expired. Please sign in again to get a new one.");
            }
            int otpAttempts = user.getLoginOtpAttempts() == null ? 0 : user.getLoginOtpAttempts();
            if (otpAttempts >= 5) {
                user.setLoginOtpHash(null); user.setLoginOtpExpiresAt(null); user.setLoginOtpAttempts(0);
                userRepository.save(user);
                throw new RuntimeException("Too many incorrect codes. Please sign in again to get a new one.");
            }
            if (!passwordEncoder.matches(req.getOtp().trim(), user.getLoginOtpHash())) {
                user.setLoginOtpAttempts(otpAttempts + 1);
                userRepository.save(user);
                throw new RuntimeException("Incorrect verification code.");
            }
            // Correct — consume it so it can't be reused, then fall through to issue a session token.
            user.setLoginOtpHash(null); user.setLoginOtpExpiresAt(null); user.setLoginOtpAttempts(0);
            userRepository.save(user);
        }
        auditService.log(user.getOrganization(), user, "LOGIN_SUCCESS", "AUTH",
            String.valueOf(user.getId()), user.getName() + " signed in", null, null, "Authentication");

        Map<String,Object> body=safe(user);
        body.put("token",jwtUtils.generateToken(user.getEmail()));
        body.put("mfaRequired",false);
        body.put("mfaSetupRequired",false);
        body.put("otpRequired",false);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me") @Transactional
    public ResponseEntity<Map<String,Object>> me(Authentication auth) {
        User user=userRepository.findByEmail(auth.getName()).orElseThrow(()->new RuntimeException("Not found"));
        return ResponseEntity.ok(safe(user));
    }

    private Map<String,Object> safe(User u) {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("userId",u.getId()); m.put("name",u.getName()); m.put("email",u.getEmail());
        m.put("role",u.getRole()!=null?u.getRole().getName():null);
        m.put("twoFactorEnabled",u.isTwoFactorEnabled());
        m.put("mustChangePassword",u.isMustChangePassword());
        if(u.getOrganization()!=null) {
            m.put("organizationId",u.getOrganization().getId());
            m.put("organizationName",u.getOrganization().getName());
            m.put("currency",u.getOrganization().getDefaultCurrency());
            m.put("locale",u.getOrganization().getLocale());
            m.put("timezone",u.getOrganization().getTimezone());
        } else { m.put("organizationId",null); m.put("organizationName",null); m.put("currency","USD"); m.put("locale","en-US"); m.put("timezone","UTC"); }
        return m;
    }
}