package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.InternalDocument;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.InternalDocumentRepository;
import com.patrick.fintech.loan_backend.service.InternalDocumentService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Internal document library — staff policies, contracts, memos, templates. Separate from
 * BorrowerFileController, which is KYC/application documents tied to a single borrower.
 * Every read/write here is org-scoped (see InternalDocumentService#getByIdForOrg).
 */
@RestController
@RequestMapping("/api/internal-documents")
@RequiredArgsConstructor
public class InternalDocumentController {

    private final InternalDocumentService docService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InternalDocumentRepository.Summary>>> list(
            @RequestParam(value = "category", required = false) String category) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(docService.list(orgId, category)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InternalDocument>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false, defaultValue = "OTHER") String category,
            @RequestParam(value = "description", required = false) String description) throws Exception {
        User user = currentUserUtil.getCurrentUser();
        InternalDocument saved = docService.upload(user.getOrganization(), user, file, title, category, description);
        // Never serialize the file bytes back in the upload response — the frontend already
        // has the file locally and only needs the saved metadata (id, name, etc.) to update its list.
        //
        // IMPORTANT: this method used to be @Transactional, which kept `saved` attached to
        // Hibernate for the whole request. Setting a managed entity's field to null (as below)
        // isn't a harmless in-memory tweak in that case — Hibernate's dirty-checking queues an
        // UPDATE for it, which fired at commit time right after this method returned and tried
        // to write NULL into the NOT NULL `data` column, failing with a constraint violation
        // that surfaced to the user as "This action was already completed or conflicts with an
        // existing record." The document had already saved successfully; only the response step
        // was broken. Removing @Transactional here lets docService.upload()'s own save() commit
        // and detach `saved` on its own (the repository save is transactional by itself), so this
        // mutation is now what it looks like: a plain, un-persisted in-memory change before we
        // serialize the response — the same safe pattern BorrowerFileService already uses.
        saved.setData(null);
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded", saved));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        InternalDocument doc = docService.getByIdForOrg(id, orgId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(doc.getFileType() != null ? doc.getFileType() : "application/octet-stream"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
            .body(doc.getData());
    }

    /** Deletion is deliberately restricted — an internal document library is easy to accidentally
     *  empty out otherwise, and unlike a borrower's own KYC upload mistake, there's no "the
     *  applicant re-uploads it" safety net here. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        User user = currentUserUtil.getCurrentUser();
        docService.delete(id, orgId, user);
        return ResponseEntity.ok(ApiResponse.ok("Document deleted", null));
    }
}
