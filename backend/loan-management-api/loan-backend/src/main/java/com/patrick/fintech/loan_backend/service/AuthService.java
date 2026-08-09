package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.dto.RegisterRequest;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class AuthService {

    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final RoleRepository         roleRepository;
    private final OrganizationRepository organizationRepository;
    private final MailService            mailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O — avoids look-alike confusion
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789"; // no 0/1 — avoids look-alike confusion
    private static final String SPECIAL = "!@#$%&*?";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    @org.springframework.beans.factory.annotation.Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       RoleRepository roleRepository,
                       OrganizationRepository organizationRepository,
                       MailService mailService) {
        this.userRepository         = userRepository;
        this.passwordEncoder        = passwordEncoder;
        this.roleRepository         = roleRepository;
        this.organizationRepository = organizationRepository;
        this.mailService            = mailService;
    }

    /** Public, unauthenticated self-registration — the person chooses their own password.
     *  Do not change this to auto-generate a password; that would break genuine signup. */
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // RoleRepository.findByName now accepts String
        String roleName = request.getRole() != null ? request.getRole() : "LOAN_OFFICER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found: " + request.getOrganizationId()));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(request.getPassword());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setOrganization(org);
        return userRepository.save(user);
    }

    /**
     * Admin-creates-a-teammate path (used by UserController, never by the public
     * /api/auth/register endpoint). The admin never types a password for someone
     * else — any "password" field on the request is ignored — the system generates
     * one, emails it to the new user's inbox, and marks the account so the first
     * login forces them to set their own password before doing anything else.
     */
    public User registerByAdmin(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }
        if (request.getEmail() == null || request.getEmail().isBlank())
            throw new RuntimeException("Email is required so we can send this user their login details.");

        String roleName = request.getRole() != null ? request.getRole() : "LOAN_OFFICER";
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));

        Organization org = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new RuntimeException("Organization not found: " + request.getOrganizationId()));

        String tempPassword = generateTempPassword();

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setRole(role);
        user.setOrganization(org);
        user = userRepository.save(user);

        mailService.sendNewUserCredentials(user, tempPassword, frontendUrl + "/login");
        return user;
    }

    /** 12 chars: at least one of each required class, rest random, then shuffled —
     *  always passes PasswordPolicy.validate() by construction. */
    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder();
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        for (int i = sb.length(); i < 12; i++) {
            sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        // Fisher-Yates shuffle so the fixed-class characters aren't always in the same positions
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }
}
