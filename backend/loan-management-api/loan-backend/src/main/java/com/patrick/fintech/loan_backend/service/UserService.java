package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserService {

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder        passwordEncoder;

    public UserService(UserRepository u, RoleRepository r,
                       OrganizationRepository o, PasswordEncoder p) {
        this.userRepository         = u;
        this.roleRepository         = r;
        this.organizationRepository = o;
        this.passwordEncoder        = p;
    }

    public User createUser(User user, Long roleId, Long orgId) {
        if (userRepository.existsByEmail(user.getEmail()))
            throw new RuntimeException("Email already exists: " + user.getEmail());
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        Organization org = organizationRepository.findById(orgId)
            .orElseThrow(() -> new RuntimeException("Org not found: " + orgId));
        user.setRole(role);
        user.setOrganization(org);
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(user.getPassword());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public List<User> getAll()       { return userRepository.findAll(); }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found: " + id));
    }

    public User update(Long id, User updated) {
        User user = getById(id);
        if (updated.getName() != null && !updated.getName().isBlank())
            user.setName(updated.getName());
        return userRepository.save(user);
    }

    /** Dedicated path for role changes. This must NOT go through update(id, user) above —
     *  that method only ever copies the `name` field onto a freshly-refetched entity, so a
     *  role set on the caller's in-memory User object (as UserController.changeRole used to
     *  do before calling update()) was silently discarded: the endpoint returned success, but
     *  the role in the database never changed. */
    public User changeRole(Long id, Role role) {
        User user = getById(id);
        user.setRole(role);
        return userRepository.save(user);
    }

    /** Changes a user's email after checking no one else already has it. Used both for admins
     *  editing another user and for self-service (where the controller additionally verifies
     *  the caller's current password before calling this). */
    public User updateEmail(Long id, String newEmail) {
        User user = getById(id);
        String normalized = newEmail.trim().toLowerCase();
        if (!normalized.equals(user.getEmail())) {
            if (userRepository.existsByEmail(normalized))
                throw new RuntimeException("Email already in use: " + normalized);
            user.setEmail(normalized);
            userRepository.save(user);
        }
        return user;
    }

    /** Admin-reset path: changes a user's password without knowing their current one. Only
     *  reachable when the caller is an admin/manager acting on someone else's account —
     *  see UserController for the self-vs-other authorization split. */
    public User updatePassword(Long id, String newPassword) {
        User user = getById(id);
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        return userRepository.save(user);
    }

    /** Self-service path: the user is changing their own password, so they must prove they
     *  know the current one first — this was previously missing entirely, meaning anyone
     *  with a valid session could silently change their own password with no verification. */
    public User changeOwnPassword(Long id, String currentPassword, String newPassword) {
        User user = getById(id);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new RuntimeException("Current password is incorrect");
        com.patrick.fintech.loan_backend.security.PasswordPolicy.validate(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    public boolean verifyPassword(Long id, String rawPassword) {
        User user = getById(id);
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /** "Delete" a staff account. This does NOT remove the row — every loan, audit log entry,
     *  document upload, and approval this user ever touched has a foreign key pointing at
     *  users.id with no cascade rule, so a real DELETE always failed with a constraint
     *  violation the moment the target had any history at all (which is to say, always, for
     *  any account actually used day to day). It surfaced to the admin as a generic
     *  "conflicts with an existing record" error.
     *
     *  Separately, hard-deleting a user referenced by the append-only audit hash chain would
     *  be the wrong move even if it didn't crash — it'd leave audit entries pointing at a
     *  user that no longer exists, undermining the tamper-evident trail this platform relies
     *  on for compliance. Deactivating (blocks login, hidden from active staff lists, history
     *  intact) is the correct operation here, not a workaround. */
    public User deactivate(Long id) {
        User user = getById(id);
        user.setStatus(User.UserStatus.SUSPENDED);
        return userRepository.save(user);
    }

    public User reactivate(Long id) {
        User user = getById(id);
        user.setStatus(User.UserStatus.ACTIVE);
        return userRepository.save(user);
    }
}
