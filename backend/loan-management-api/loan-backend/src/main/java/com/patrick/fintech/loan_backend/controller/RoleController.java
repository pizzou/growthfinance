package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.model.Role;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.RoleService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    public RoleController(RoleService roleService, AuditService auditService, CurrentUserUtil currentUserUtil) {
        this.roleService = roleService;
        this.auditService = auditService;
        this.currentUserUtil = currentUserUtil;
    }

    @PostMapping
    public ResponseEntity<Role> create(@RequestBody Role role) {
        Role saved = roleService.save(role);
        var user = currentUserUtil.getCurrentUser();
        auditService.log(user != null ? user.getOrganization() : null, user, "ROLE_CREATED", "ROLE",
            String.valueOf(saved.getId()), "Created role " + saved.getName(), null, null, "Roles & Permissions");
        return ResponseEntity.ok(saved);
    }
    @GetMapping  public ResponseEntity<List<Role>> getAll() { return ResponseEntity.ok(roleService.getAll()); }
}