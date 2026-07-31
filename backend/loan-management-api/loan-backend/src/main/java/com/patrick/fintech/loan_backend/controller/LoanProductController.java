package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Loan;
import com.patrick.fintech.loan_backend.model.LoanProduct;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.LoanProductRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.patrick.fintech.loan_backend.model.DocumentType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-managed loan product catalog. These are the REAL products — their
 * rate, min/max amount, and term bounds are what LoanService actually
 * applies when a loan is created, and what the public website's Services
 * page advertises. One source of truth for both.
 */
@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductRepository productRepo;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil        currentUserUtil;
    private final AuditService           auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LoanProduct>>> list() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(productRepo.findByOrganization_IdOrderByDisplayOrderAsc(orgId)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProduct>> create(@RequestBody Map<String,Object> body) {
        Organization org = orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));

        LoanProduct p = new LoanProduct();
        p.setOrganization(org);
        applyFields(p, body);
        p = productRepo.save(p);

        auditService.log(org, currentUserUtil.getCurrentUser(), "LOAN_PRODUCT_CREATED", "LOAN_PRODUCT",
            p.getId().toString(), "Created product \"" + p.getName() + "\" (" + p.getInterestRate() + "%)");
        return ResponseEntity.ok(ApiResponse.ok("Product created", p));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProduct>> update(@PathVariable Long id, @RequestBody Map<String,Object> body) {
        LoanProduct p = productRepo.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        assertOwnership(p);
        applyFields(p, body);
        p = productRepo.save(p);

        auditService.log(p.getOrganization(), currentUserUtil.getCurrentUser(), "LOAN_PRODUCT_UPDATED", "LOAN_PRODUCT",
            p.getId().toString(), "Updated product \"" + p.getName() + "\"");
        return ResponseEntity.ok(ApiResponse.ok("Product updated", p));
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProduct>> toggleActive(@PathVariable Long id) {
        LoanProduct p = productRepo.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        assertOwnership(p);
        p.setActive(!Boolean.TRUE.equals(p.getActive()));
        p = productRepo.save(p);
        auditService.log(p.getOrganization(), currentUserUtil.getCurrentUser(), "LOAN_PRODUCT_TOGGLED", "LOAN_PRODUCT",
            p.getId().toString(), p.getName() + " set to " + (p.getActive() ? "ACTIVE" : "INACTIVE"));
        return ResponseEntity.ok(ApiResponse.ok(p));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(@PathVariable Long id) {
        LoanProduct p = productRepo.findById(id).orElseThrow(() -> new RuntimeException("Product not found"));
        assertOwnership(p);
        productRepo.delete(p);
        auditService.log(p.getOrganization(), currentUserUtil.getCurrentUser(), "LOAN_PRODUCT_DELETED", "LOAN_PRODUCT",
            id.toString(), "Deleted product \"" + p.getName() + "\"");
        return ResponseEntity.ok(ApiResponse.ok("Product deleted"));
    }

    private void assertOwnership(LoanProduct p) {
        if (!p.getOrganization().getId().equals(currentUserUtil.getCurrentOrganizationId()))
            throw new RuntimeException("Access denied");
    }

    private void applyFields(LoanProduct p, Map<String, Object> body) {

    if (body.get("name") != null)
        p.setName(body.get("name").toString());

    if (body.get("icon") != null)
        p.setIcon(body.get("icon").toString());

    if (body.get("description") != null)
        p.setDescription(body.get("description").toString());

    if (body.get("loanType") != null)
        p.setLoanType(Loan.LoanType.valueOf(body.get("loanType").toString().toUpperCase()));

    if (body.get("interestRate") != null)
        p.setInterestRate(num(body.get("interestRate")));

    if (body.get("interestRateType") != null) {
        String t = body.get("interestRateType").toString().toUpperCase();

        if (!t.equals("MONTHLY") && !t.equals("ANNUAL")) {
            throw new RuntimeException("interestRateType must be MONTHLY or ANNUAL");
        }

        p.setInterestRateType(t);
    }

    if (body.get("minAmount") != null)
        p.setMinAmount(num(body.get("minAmount")));

    // unlimited products have no upper limit
    if (Boolean.TRUE.equals(body.get("unlimited"))) {
        p.setMaxAmount(null);
    } else if (body.containsKey("maxAmount")) {
        p.setMaxAmount(
                body.get("maxAmount") != null
                        ? num(body.get("maxAmount"))
                        : null
        );
    }

    if (body.get("processingFeePercent") != null)
        p.setProcessingFeePercent(num(body.get("processingFeePercent")));

    if (body.get("minTermMonths") != null)
        p.setMinTermMonths((int) Math.round(num(body.get("minTermMonths"))));

    if (body.get("maxTermMonths") != null)
        p.setMaxTermMonths((int) Math.round(num(body.get("maxTermMonths"))));

    if (body.get("displayOrder") != null)
        p.setDisplayOrder((int) Math.round(num(body.get("displayOrder"))));

    if (body.get("active") != null)
        p.setActive(Boolean.parseBoolean(body.get("active").toString()));

    /*
     * Required documents
     */
    if (body.get("requiredDocumentTypes") != null) {

        String raw = body.get("requiredDocumentTypes").toString();

        List<String> validatedTypes = new ArrayList<>();

        for (String value : raw.split(",")) {

            String type = value.trim().toUpperCase();

            if (type.isBlank())
                continue;

            try {
                DocumentType.valueOf(type);
                validatedTypes.add(type);
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Unknown document type: " + type);
            }
        }

        p.setRequiredDocumentTypes(
                validatedTypes.isEmpty()
                        ? null
                        : String.join(",", validatedTypes)
        );
    }

    /*
     * Validation
     */
    if (p.getMinAmount() != null
            && p.getMaxAmount() != null
            && p.getMinAmount() > p.getMaxAmount()) {

        throw new RuntimeException(
                "Minimum amount cannot exceed maximum amount");
    }

    if (p.getMinTermMonths() != null
            && p.getMaxTermMonths() != null
            && p.getMinTermMonths() > p.getMaxTermMonths()) {

        throw new RuntimeException(
                "Minimum term cannot exceed maximum term");
    }
}
    private Double num(Object o) { return Double.valueOf(o.toString()); }
}
