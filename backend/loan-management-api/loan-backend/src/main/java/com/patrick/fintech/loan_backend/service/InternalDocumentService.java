package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.InternalDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalDocumentService {

    private final InternalDocumentRepository docRepo;
    private final AuditService auditService;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/jpg", "image/png", "image/webp",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain", "text/csv");
        
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024; // 20MB

    public static final Set<String> CATEGORIES = Set.of(
        "POLICY", "CONTRACT", "MEMO", "TEMPLATE", "BOARD_MINUTES", "COMPLIANCE", "OTHER");

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file was received.");
        if (file.getSize() > MAX_FILE_BYTES) throw new RuntimeException("File is too large — please upload something under 20MB.");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("Unsupported file type. Please upload a PDF, Word, Excel, image, or text file.");
        }
    }

    public InternalDocument upload(Organization org, User uploadedBy, MultipartFile file,
                                    String title, String category, String description) throws IOException {
        if (org == null) throw new RuntimeException("Could not determine your organization — please sign out and back in, then try again.");
        validate(file);

        String cat = (category != null && CATEGORIES.contains(category.toUpperCase())) ? category.toUpperCase() : "OTHER";

        // Extract and clean raw filename inputs
        String originalFileName = (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
            ? file.getOriginalFilename()
            : "document-" + System.currentTimeMillis();

        String providedTitle = (title != null && !title.isBlank()) ? title.trim() : "";
        
        // 🔑 THE PERMANENT ARCHITECTURAL FIX: 
        // If no unique title string was supplied, append a random short suffix token mapping string 
        // to dynamically bypass the UNIQUE(organization_id, title) indexing bottleneck constraint safely!
        String resolvedTitle;
        if (!providedTitle.isEmpty()) {
            resolvedTitle = providedTitle;
        } else {
            String baseName = originalFileName.contains(".") 
                ? originalFileName.substring(0, originalFileName.lastIndexOf('.')) 
                : originalFileName;
            resolvedTitle = baseName + " (" + UUID.randomUUID().toString().substring(0, 5) + ")";
        }

        if (file.getContentType() == null || file.getContentType().isBlank())
            throw new RuntimeException("Could not determine this file's type. Please try a different file.");
        if (file.getSize() <= 0)
            throw new RuntimeException("This file appears to be empty.");

        log.info("[INTERNAL UPLOAD] Processing Document — Category: {}, Safe Resolved Title: {}", cat, resolvedTitle);

        InternalDocument doc = InternalDocument.builder()
            .organization(org)
            .title(resolvedTitle)
            .category(cat)
            .description(description)
            .fileName(originalFileName)
            .fileType(file.getContentType())
            .fileSize(file.getSize())
            .data(file.getBytes())
            .uploadedBy(uploadedBy)
            .build();

        try {
            doc = docRepo.save(doc);
        } catch (Exception e) {
            log.error("[DATABAS CRASH] Unique constraint or format token assignment collapsed:", e);
            throw new RuntimeException("A document with an identical title signature already exists inside this category folder block. Please rename your document title and re-submit.");
        }

        auditService.log(org, uploadedBy, "INTERNAL_DOCUMENT_UPLOADED", "INTERNAL_DOCUMENT",
            String.valueOf(doc.getId()), "Uploaded \"" + doc.getTitle() + "\" (" + cat + ")",
            null, null, "Documents & KYC");
            
        return doc;
    }

    public List<InternalDocumentRepository.Summary> list(Long orgId, String category) {
        if (category != null && !category.isBlank()) return docRepo.findSummariesByOrgAndCategory(orgId, category.toUpperCase());
        return docRepo.findSummariesByOrg(orgId);
    }

    public InternalDocument getByIdForOrg(Long id, Long orgId) {
        InternalDocument doc = docRepo.findById(id).orElseThrow(() -> new RuntimeException("Document not found: " + id));
        if (!doc.getOrganization().getId().equals(orgId)) throw new RuntimeException("Document not found: " + id);
        return doc;
    }

    public void delete(Long id, Long orgId, User deletedBy) {
        InternalDocument doc = getByIdForOrg(id, orgId);
        docRepo.deleteById(id);
        auditService.log(doc.getOrganization(), deletedBy, "INTERNAL_DOCUMENT_DELETED", "INTERNAL_DOCUMENT",
            String.valueOf(id), "Deleted \"" + doc.getTitle() + "\"", null, null, "Documents & KYC");
    }
}