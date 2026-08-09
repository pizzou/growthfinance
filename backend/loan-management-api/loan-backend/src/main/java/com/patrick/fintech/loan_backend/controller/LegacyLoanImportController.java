package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.dto.ImportRowResult;
import com.patrick.fintech.loan_backend.model.ImportBatch;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.repository.ImportBatchRepository;
import com.patrick.fintech.loan_backend.repository.OrganizationRepository;
import com.patrick.fintech.loan_backend.service.LegacyLoanImportService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bulk import of a client's pre-existing manual (Excel/CSV) loan ledger. Admin/Manager only —
 * this creates real borrower and loan records directly, bypassing normal origination.
 */
@RestController
@RequestMapping("/api/import/legacy-loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class LegacyLoanImportController {

    private final LegacyLoanImportService importService;
    private final ImportBatchRepository importBatchRepo;
    private final OrganizationRepository orgRepo;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"legacy-loan-import-template.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(importService.buildCsvTemplate());
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<List<ImportRowResult>>> preview(@RequestParam("file") MultipartFile file) throws Exception {
        Organization org = currentOrg();
        List<ImportRowResult> results = importService.preview(file.getOriginalFilename(), file.getInputStream(), org);
        long ok = results.stream().filter(ImportRowResult::isSuccess).count();
        return ResponseEntity.ok(ApiResponse.ok(
            ok + "/" + results.size() + " rows would import successfully. Nothing has been saved yet.", results));
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<ImportBatch>> commit(@RequestParam("file") MultipartFile file) throws Exception {
        Organization org = currentOrg();
        ImportBatch batch = importService.commit(
            file.getOriginalFilename(), file.getInputStream(), org, currentUserUtil.getCurrentUser());
        return ResponseEntity.ok(ApiResponse.ok(
            "Imported " + batch.getSuccessCount() + "/" + batch.getTotalRows() + " rows.", batch));
    }

    @GetMapping("/batches")
    public ResponseEntity<ApiResponse<List<ImportBatch>>> batches() {
        return ResponseEntity.ok(ApiResponse.ok(
            importBatchRepo.findByOrganization_IdOrderByCreatedAtDesc(currentUserUtil.getCurrentOrganizationId())));
    }

    private Organization currentOrg() {
        return orgRepo.findById(currentUserUtil.getCurrentOrganizationId())
            .orElseThrow(() -> new RuntimeException("Organization not found"));
    }
}
