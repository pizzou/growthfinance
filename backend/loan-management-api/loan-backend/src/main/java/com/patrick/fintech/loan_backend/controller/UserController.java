package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.RegisterRequest;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.RoleRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.AuthService;
import com.patrick.fintech.loan_backend.service.UserService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService     userService;
    private final AuthService     authService;
    private final RoleRepository  roleRepository;
    private final CurrentUserUtil currentUserUtil;
    private final AuditService    auditService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','INSTITUTION_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> create(@RequestBody RegisterRequest req) {
        if (req.getOrganizationId() == null)
            req.setOrganizationId(currentUserUtil.getCurrentOrganizationId());
        User user = authService.registerByAdmin(req);
        auditService.log(user.getOrganization(), currentUserUtil.getCurrentUser(), "USER_CREATED", "USER",
            String.valueOf(user.getId()), "Created user " + user.getName() + " (" + user.getEmail() + ") — credentials emailed",
            null, null, "User Management");
        return ResponseEntity.ok(ApiResponse.ok("User created — login details have been emailed to them", safeUser(user)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getAll() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        List<Map<String,Object>> users = userService.getAll().stream()
            .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
            .map(this::safeUser).toList();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getById(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        User user  = userService.getById(id);
        if (!user.getOrganization().getId().equals(orgId))
            throw new RuntimeException("Access denied");
        return ResponseEntity.ok(ApiResponse.ok(safeUser(user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> update(
            @PathVariable Long id, @RequestBody Map<String,String> body) {
        User user = userService.getById(id);
        Long callerOrgId = currentUserUtil.getCurrentOrganizationId();
        Long callerId    = currentUserUtil.getCurrentUserId();
        boolean isSelf   = id.equals(callerId);
        boolean isAdmin  = List.of("ADMIN", "MANAGER").contains(currentUserUtil.getCurrentUser().getRole().getName());

        // Previously this endpoint had no authorization check at all — any authenticated user
        // could edit any other user's name/password by guessing an id, even across organizations.
        if (!user.getOrganization().getId().equals(callerOrgId))
            throw new RuntimeException("Access denied");
        if (!isSelf && !isAdmin)
            throw new RuntimeException("Only an admin or manager can edit another user's account");

        if (body.containsKey("name") && body.get("name") != null) user.setName(body.get("name"));

        if (body.containsKey("email") && body.get("email") != null && !body.get("email").isBlank()) {
            String newEmail = body.get("email");
            if (isSelf) {
                // Editing your own email requires proving you know your current password —
                // otherwise a hijacked/left-open session could silently redirect someone's
                // account to an attacker-controlled inbox.
                String currentPassword = body.get("currentPassword");
                if (currentPassword == null || currentPassword.isBlank())
                    throw new RuntimeException("Enter your current password to change your email");
                if (!userService.verifyPassword(id, currentPassword))
                    throw new RuntimeException("Current password is incorrect");
            }
            user = userService.updateEmail(id, newEmail);
        }

        if (body.containsKey("password") && body.get("password") != null && !body.get("password").isBlank()) {
            if (isSelf) {
                String currentPassword = body.get("currentPassword");
                user = userService.changeOwnPassword(id, currentPassword, body.get("password"));
            } else {
                // Admin resetting someone else's password — they don't need that person's
                // current password, matching existing user-management behavior.
                user = userService.updatePassword(id, body.get("password"));
            }
        }

        User updated = userService.update(id, user);
        auditService.log(updated.getOrganization(), currentUserUtil.getCurrentUser(), "USER_UPDATED", "USER",
            String.valueOf(updated.getId()), "Updated user " + updated.getName(), null, null, "User Management");
        return ResponseEntity.ok(ApiResponse.ok("Updated", safeUser(updated)));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> changeRole(
            @PathVariable Long id, @RequestBody Map<String,String> body) {
        String roleName = body.get("role");
        if (roleName == null || roleName.isBlank()) throw new RuntimeException("role is required");
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        User user = userService.getById(id);
        if (!user.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        var role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new RuntimeException("Unknown role: " + roleName));
        String previousRole = user.getRole() != null ? user.getRole().getName() : null;
        // Was: user.setRole(role); userService.update(id, user) — update() only ever copies
        // the `name` field onto a fresh re-fetch of the user, so the role set here was
        // silently discarded every time. This endpoint returned "success" but never actually
        // changed anyone's role. changeRole() below is the real fix.
        User updated = userService.changeRole(id, role);
        auditService.log(updated.getOrganization(), currentUserUtil.getCurrentUser(), "USER_ROLE_CHANGED", "USER",
            String.valueOf(updated.getId()), "Changed role of " + updated.getName() + " to " + roleName,
            previousRole, roleName, "User Management");
        return ResponseEntity.ok(ApiResponse.ok("Role updated to " + roleName, safeUser(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (id.equals(currentUserUtil.getCurrentUserId()))
            throw new RuntimeException("Cannot delete your own account");
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        User target = userService.getById(id);
        // Previously missing entirely — an admin could deactivate/delete a user in a
        // *different* organization just by guessing an id, the same cross-org gap that
        // existed on PUT /{id} before it was fixed.
        if (!target.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        // Was a hard userService.delete(id) -> userRepository.deleteById(id). Every user this
        // platform's ever used has rows elsewhere (loans, audit logs, uploads, approvals...)
        // pointing at them by foreign key with no cascade rule, so that delete always failed
        // with a database constraint violation — surfaced to the admin as a generic "conflicts
        // with an existing record" error. Deactivating instead blocks their login and removes
        // them from active use without breaking referential integrity or the audit trail.
        userService.deactivate(id);
        auditService.log(target.getOrganization(), currentUserUtil.getCurrentUser(), "USER_DEACTIVATED", "USER",
            String.valueOf(id), "Deactivated user " + target.getName() + " (" + target.getEmail() + ")",
            null, null, "User Management");
        return ResponseEntity.ok(ApiResponse.ok("User deactivated — their login is disabled and their history is preserved"));
    }

    /** Undoes delete()/deactivate() above — an admin's only way to walk back a deactivation
     *  short of editing the database directly. */
    @PutMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String,Object>>> reactivate(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        User target = userService.getById(id);
        if (!target.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        User updated = userService.reactivate(id);
        auditService.log(updated.getOrganization(), currentUserUtil.getCurrentUser(), "USER_REACTIVATED", "USER",
            String.valueOf(id), "Reactivated user " + updated.getName() + " (" + updated.getEmail() + ")",
            null, null, "User Management");
        return ResponseEntity.ok(ApiResponse.ok("User reactivated", safeUser(updated)));
    }

    private Map<String,Object> safeUser(User u) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",           u.getId());
        m.put("name",         u.getName());
        m.put("email",        u.getEmail());
        // Role.getName() now returns String — no .name() call needed
        m.put("role",         u.getRole() != null
            ? Map.of("id", u.getRole().getId(), "name", u.getRole().getName()) : null);
        m.put("organization", u.getOrganization() != null
            ? Map.of("id", u.getOrganization().getId(), "name", u.getOrganization().getName()) : null);
        m.put("mustChangePassword", u.isMustChangePassword());
        m.put("status", u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
        return m;
    }
}