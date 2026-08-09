package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Branch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.BranchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.repository.UserRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository       branchRepo;
    private final OrganizationRepository orgRepo;
    private final UserRepository         userRepo;
    private final CurrentUserUtil        currentUserUtil;
    private final AuditService           auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Branch>>> list() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(branchRepo.findByOrganization_Id(orgId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Branch>> create(@RequestBody Map<String,Object> body) {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));
        Branch b = Branch.builder()
            .organization(org)
            .name(str(body,"name")).code(str(body,"code")).address(str(body,"address"))
            .city(str(body,"city")).phone(str(body,"phone"))
            .active(true)
            .build();
        if (body.get("managerId") != null) {
            userRepo.findById(Long.valueOf(body.get("managerId").toString())).ifPresent(b::setManager);
        }
        b = branchRepo.save(b);
        auditService.log(org, currentUserUtil.getCurrentUser(), "BRANCH_CREATED", "BRANCH",
            b.getId().toString(), "Branch \"" + b.getName() + "\" created");
        return ResponseEntity.ok(ApiResponse.ok("Branch created", b));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Branch>> update(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        Branch b = branchRepo.findById(id).orElseThrow(() -> new RuntimeException("Branch not found"));
        assertOwnership(b);
        if (body.get("name")    != null) b.setName(str(body,"name"));
        if (body.get("code")    != null) b.setCode(str(body,"code"));
        if (body.get("address") != null) b.setAddress(str(body,"address"));
        if (body.get("city")    != null) b.setCity(str(body,"city"));
        if (body.get("phone")   != null) b.setPhone(str(body,"phone"));
        if (body.get("active")  != null) b.setActive(Boolean.parseBoolean(body.get("active").toString()));
        if (body.get("managerId") != null) userRepo.findById(Long.valueOf(body.get("managerId").toString())).ifPresent(b::setManager);
        b = branchRepo.save(b);
        auditService.log(b.getOrganization(), currentUserUtil.getCurrentUser(), "BRANCH_UPDATED", "BRANCH",
            id.toString(), "Branch \"" + b.getName() + "\" updated");
        return ResponseEntity.ok(ApiResponse.ok("Branch updated", b));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        Branch b = branchRepo.findById(id).orElseThrow(() -> new RuntimeException("Branch not found"));
        assertOwnership(b);
        branchRepo.delete(b);
        auditService.log(b.getOrganization(), currentUserUtil.getCurrentUser(), "BRANCH_DELETED", "BRANCH",
            id.toString(), "Branch \"" + b.getName() + "\" deleted");
        return ResponseEntity.ok(ApiResponse.ok("Branch deleted"));
    }

    private void assertOwnership(Branch b) {
        if (!b.getOrganization().getId().equals(currentUserUtil.getCurrentOrganizationId()))
            throw new RuntimeException("Access denied");
    }

    private String str(Map<String,Object> b, String k) { return b.get(k) != null ? b.get(k).toString() : null; }
}
