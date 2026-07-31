package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.service.BorrowerFileService;
import com.patrick.fintech.loan_backend.service.MailService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.model.DocumentType;
import java.util.List;
import java.util.Map;

/**
 * Staff-side KYC document endpoints. Every read/write here is scoped to the caller's
 * organization (see BorrowerFileService#getByIdForOrg) — a file ID alone is not enough
 * to fetch, preview, verify, or delete a document belonging to a different tenant.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class BorrowerFileController {

    private final BorrowerFileService fileService;
    private final BorrowerRepository  borrowerRepository;
    private final AuditService        auditService;
    private final MailService mailService;
    private final CurrentUserUtil     currentUserUtil;

    @PostMapping("/upload/{borrowerId}")
@org.springframework.transaction.annotation.Transactional
public ResponseEntity<ApiResponse<BorrowerFile>> upload(
        @PathVariable Long borrowerId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") String documentType) throws Exception {

    User user = currentUserUtil.getCurrentUser();

    var borrower = borrowerRepository.findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));

    if (!borrower.getOrganization().getId().equals(user.getOrganization().getId())) {
        throw new RuntimeException("Access denied");
    }

    DocumentType type;

    try {
        type = DocumentType.valueOf(documentType.toUpperCase());
    } catch (IllegalArgumentException ex) {
        type = DocumentType.OTHER;
    }

    BorrowerFile saved = fileService.upload(
            borrowerId,
            file,
            type,
            false
    );

    auditService.log(
            saved.getBorrower().getOrganization(),
            user,
            "DOCUMENT_UPLOADED",
            "BORROWER_FILE",
            String.valueOf(saved.getId()),
            "Uploaded " + type + " (" + saved.getFileName() + ") for borrower #" + borrowerId,
            null,
            null,
            "Documents & KYC"
    );

    return ResponseEntity.ok(ApiResponse.ok("File uploaded", saved));
}

    /** All documents for a borrower — staff KYC review list (Loan Officer opening an application). */
    @GetMapping("/borrower/{borrowerId}")
    public ResponseEntity<ApiResponse<List<BorrowerFile>>> getFiles(@PathVariable Long borrowerId) {
        User user = currentUserUtil.getCurrentUser();
        var borrower = borrowerRepository.findById(borrowerId)
            .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
        if (!borrower.getOrganization().getId().equals(user.getOrganization().getId()))
            throw new RuntimeException("Access denied");
        return ResponseEntity.ok(ApiResponse.ok(fileService.getByBorrower(borrowerId)));
    }

    /** Attachment download — forces "Save As". */
    @GetMapping("/download/{fileId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
        return serveFile(fileId, "attachment", "DOCUMENT_DOWNLOADED", "Downloaded");
    }

    /** Inline view — for the "Preview" / "Open in new tab" buttons; browser renders images/PDFs directly. */
    @GetMapping("/preview/{fileId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId) {
        return serveFile(fileId, "inline", "DOCUMENT_PREVIEWED", "Previewed");
    }

    private ResponseEntity<byte[]> serveFile(Long fileId, String disposition, String action, String verb) {
        User user = currentUserUtil.getCurrentUser();
        BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
        auditService.log(file.getBorrower().getOrganization(), user,
            action, "BORROWER_FILE", String.valueOf(fileId),
            verb + " " + file.getDocumentType() + " (" + file.getFileName() + ")",
            null, null, "Documents & KYC");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                file.getFileType() != null ? file.getFileType() : "application/octet-stream"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                disposition + "; filename=\"" + file.getFileName() + "\"")
            .body(file.getData());
    }

    /** Staff verification decision on a single document — Verified / Rejected / Replacement Requested. */
    @PatchMapping("/{fileId}/verify")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER')")
@org.springframework.transaction.annotation.Transactional
public ResponseEntity<ApiResponse<BorrowerFile>> verify(
        @PathVariable Long fileId,
        @RequestBody Map<String, String> body) {

    User user = currentUserUtil.getCurrentUser();

    String statusValue = body.get("status");
    String comment = body.get("comment");

    VerificationStatus status;

    try {
        status = VerificationStatus.valueOf(statusValue.toUpperCase());
    } catch (Exception ex) {
        throw new RuntimeException("Invalid verification status: " + statusValue);
    }

    BorrowerFile updated = fileService.verify(
            fileId,
            user.getOrganization().getId(),
            status,
            comment,
            user.getName()
    );

    auditService.log(
            updated.getBorrower().getOrganization(),
            user,
            "DOCUMENT_" + status.name(),
            "BORROWER_FILE",
            String.valueOf(fileId),
            updated.getDocumentType().name() + " (" + updated.getFileName() + ") marked "
                    + status.name()
                    + (comment != null && !comment.isBlank()
                        ? ": " + comment
                        : ""),
            null,
            null,
            "Documents & KYC"
    );

    if (updated.getBorrower() != null &&
        updated.getBorrower().getEmail() != null) {

        try {

            switch (status) {

                case VERIFIED ->
                    mailService.sendDocumentVerified(
                            updated.getBorrower(),
                            updated.getDocumentType().name());

                case REJECTED ->
                    mailService.sendDocumentRejected(
                            updated.getBorrower(),
                            updated.getDocumentType().name(),
                            comment);

                case REPLACEMENT_REQUESTED ->
                    mailService.sendDocumentReplacementRequested(
                            updated.getBorrower(),
                            updated.getDocumentType().name(),
                            comment);

                default -> {
                }
            }

        } catch (Exception ignored) {
        }
    }

    return ResponseEntity.ok(
            ApiResponse.ok(
                    "Document " + status.name().toLowerCase().replace('_', ' '),
                    updated));
}

    @DeleteMapping("/{fileId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> delete(@PathVariable Long fileId) {
        User user = currentUserUtil.getCurrentUser();
        BorrowerFile file = fileService.getByIdForOrg(fileId, user.getOrganization().getId());
        auditService.log(file.getBorrower().getOrganization(), user,
            "DOCUMENT_DELETED", "BORROWER_FILE", String.valueOf(fileId),
            "Deleted " + file.getDocumentType() + " (" + file.getFileName() + ") for borrower #" + file.getBorrower().getId(),
            null, null, "Documents & KYC");
        fileService.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}