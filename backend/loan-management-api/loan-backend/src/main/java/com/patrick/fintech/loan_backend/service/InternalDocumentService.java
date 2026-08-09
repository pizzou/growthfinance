package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.InternalDocument;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.InternalDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalDocumentService {

private final InternalDocumentRepository docRepo;
private final AuditService auditService;

/*
 * ================================================================
 * FILE SECURITY POLICY
 * ================================================================
 */

private static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;

private static final int MAX_TITLE_LENGTH = 255;
private static final int MAX_DESCRIPTION_LENGTH = 5000;
private static final int MAX_FILENAME_LENGTH = 255;

private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",

        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/webp",

        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",

        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",

        "text/plain",
        "text/csv"
);

public static final Set<String> CATEGORIES = Set.of(
        "POLICY",
        "CONTRACT",
        "MEMO",
        "TEMPLATE",
        "BOARD_MINUTES",
        "COMPLIANCE",
        "OTHER"
);

private static final String DEFAULT_CATEGORY = "OTHER";

/*
 * ================================================================
 * UPLOAD
 * ================================================================
 */

/**
 * Uploads and persists an internal organization document.
 *
 * Security guarantees:
 *
 * - Organization must be present and have an ID.
 * - Uploaded user must belong to the same organization when provided.
 * - File must exist and be non-empty.
 * - File must not exceed MAX_FILE_BYTES.
 * - MIME type is normalized.
 * - File extension is sanitized.
 * - Title and description are length-limited.
 * - File content signatures are checked for supported binary types.
 * - SHA-256 hash is calculated for integrity logging.
 * - Database persistence and audit are part of the same transaction.
 */
@Transactional
public InternalDocument upload(
        Organization org,
        User uploadedBy,
        MultipartFile file,
        String title,
        String category,
        String description
) throws IOException {

    /*
     * ------------------------------------------------------------
     * ORGANIZATION VALIDATION
     * ------------------------------------------------------------
     */

    validateOrganization(org);

    /*
     * ------------------------------------------------------------
     * USER ORGANIZATION VALIDATION
     * ------------------------------------------------------------
     */

    validateUploaderOrganization(
            org,
            uploadedBy
    );

    /*
     * ------------------------------------------------------------
     * FILE VALIDATION
     * ------------------------------------------------------------
     */

    validateFile(file);

    String contentType =
            normalizeContentType(
                    file.getContentType()
            );

    if (!ALLOWED_TYPES.contains(contentType)) {

        throw new IllegalArgumentException(
                "Unsupported file type."
        );
    }

    /*
     * ------------------------------------------------------------
     * READ FILE
     * ------------------------------------------------------------
     *
     * The configured maximum is 20 MB, so the application has
     * an explicit upper memory boundary for this operation.
     */

    byte[] data;

    try {

        data = file.getBytes();

    } catch (IOException e) {

        log.error(
                "Failed to read uploaded document. orgId={}",
                org.getId(),
                e
        );

        throw new IOException(
                "Unable to read the uploaded document.",
                e
        );
    }

    if (data.length == 0) {

        throw new IllegalArgumentException(
                "The uploaded document is empty."
        );
    }

    if (data.length > MAX_FILE_BYTES) {

        throw new IllegalArgumentException(
                "The uploaded document exceeds the maximum allowed size of 20 MB."
        );
    }

    /*
     * ------------------------------------------------------------
     * CONTENT SIGNATURE VALIDATION
     * ------------------------------------------------------------
     *
     * MIME type supplied by the browser/client is not trusted.
     */

    validateFileSignature(
            data,
            contentType
    );

    /*
     * ------------------------------------------------------------
     * SAFE ORIGINAL FILENAME
     * ------------------------------------------------------------
     */

    String originalFileName =
            sanitizeFileName(
                    file.getOriginalFilename()
            );

    if (originalFileName == null || originalFileName.isBlank()) {

        originalFileName =
                "document-" +
                        UUID.randomUUID() +
                        extensionForContentType(
                                contentType
                        );
    }

    /*
     * ------------------------------------------------------------
     * CATEGORY
     * ------------------------------------------------------------
     */

    String normalizedCategory =
            normalizeCategory(
                    category
            );

    /*
     * ------------------------------------------------------------
     * TITLE
     * ------------------------------------------------------------
     */

    String resolvedTitle =
            resolveTitle(
                    title,
                    originalFileName
            );

    /*
     * ------------------------------------------------------------
     * DESCRIPTION
     * ------------------------------------------------------------
     */

    String normalizedDescription =
            normalizeDescription(
                    description
            );

    /*
     * ------------------------------------------------------------
     * FILE HASH
     * ------------------------------------------------------------
     */

    String sha256 =
            calculateSha256(
                    data
            );

    /*
     * ------------------------------------------------------------
     * CREATE ENTITY
     * ------------------------------------------------------------
     */

    InternalDocument doc =
            InternalDocument.builder()
                    .organization(org)
                    .title(resolvedTitle)
                    .category(normalizedCategory)
                    .description(normalizedDescription)
                    .fileName(originalFileName)
                    .fileType(contentType)
                    .fileSize((long) data.length)
                    .data(data)
                    .uploadedBy(uploadedBy)
                    .build();

    /*
     * ------------------------------------------------------------
     * PERSIST
     * ------------------------------------------------------------
     */

    try {

        doc =
                docRepo.save(
                        doc
                );

    } catch (DataIntegrityViolationException e) {

        /*
         * Do not expose raw database errors to the client.
         */

        log.warn(
                "Document persistence constraint violation. " +
                        "orgId={}, category={}, title={}, sha256={}",
                org.getId(),
                normalizedCategory,
                resolvedTitle,
                sha256,
                e
        );

        /*
         * The most likely cause in the current architecture is
         * the unique organization/title constraint.
         *
         * Instead of exposing the database constraint, provide
         * a safe business-level error.
         */

        throw new IllegalStateException(
                "A document with this title already exists. " +
                        "Please choose a different document title."
        );
    }

    /*
     * ------------------------------------------------------------
     * AUDIT
     * ------------------------------------------------------------
     *
     * Audit is intentionally inside the transaction.
     *
     * If the audit operation throws a runtime exception, the
     * document transaction is rolled back instead of leaving
     * an unaudited financial-system document in the database.
     */

    try {

        auditService.log(
                org,
                uploadedBy,
                "INTERNAL_DOCUMENT_UPLOADED",
                "INTERNAL_DOCUMENT",
                String.valueOf(doc.getId()),
                "Uploaded internal document \"" +
                        doc.getTitle() +
                        "\" (" +
                        normalizedCategory +
                        ")",
                null,
                null,
                "Documents & KYC"
        );

    } catch (RuntimeException e) {

        log.error(
                "Document audit failed. Rolling back document upload. " +
                        "orgId={}, documentId={}, sha256={}",
                org.getId(),
                doc.getId(),
                sha256,
                e
        );

        throw new IllegalStateException(
                "The document could not be securely recorded. " +
                        "Please try again.",
                e
        );
    }

    /*
     * ------------------------------------------------------------
     * SECURITY LOG
     * ------------------------------------------------------------
     *
     * Do not log the file contents or full filename unnecessarily.
     */

    log.info(
            "Internal document uploaded successfully. " +
                    "orgId={}, documentId={}, category={}, " +
                    "fileType={}, fileSize={}, sha256={}",
            org.getId(),
            doc.getId(),
            normalizedCategory,
            contentType,
            data.length,
            sha256
    );

    return doc;
}

/*
 * ================================================================
 * LIST DOCUMENTS
 * ================================================================
 */

@Transactional(readOnly = true)
public List<InternalDocumentRepository.Summary> list(
        Long orgId,
        String category
) {

    validateOrganizationId(
            orgId
    );

    String normalizedCategory =
            normalizeCategoryOrNull(
                    category
            );

    if (normalizedCategory != null) {

        return docRepo.findSummariesByOrgAndCategory(
                orgId,
                normalizedCategory
        );
    }

    return docRepo.findSummariesByOrg(
            orgId
    );
}

/*
 * ================================================================
 * GET DOCUMENT
 * ================================================================
 */

@Transactional(readOnly = true)
public InternalDocument getByIdForOrg(
        Long id,
        Long orgId
) {

    validateDocumentId(
            id
    );

    validateOrganizationId(
            orgId
    );

    InternalDocument doc =
            docRepo.findById(
                    id
            ).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Document not found."
                    )
            );

    /*
     * Organization ownership must be checked before returning
     * document data.
     */

    if (
            doc.getOrganization() == null
                    || doc.getOrganization().getId() == null
                    || !doc.getOrganization()
                    .getId()
                    .equals(orgId)
    ) {

        /*
         * Do not reveal that the document exists in another
         * organization.
         */

        throw new IllegalArgumentException(
                "Document not found."
        );
    }

    return doc;
}

/*
 * ================================================================
 * DELETE DOCUMENT
 * ================================================================
 */

@Transactional
public void delete(
        Long id,
        Long orgId,
        User deletedBy
) {

    validateDocumentId(
            id
    );

    validateOrganizationId(
            orgId
    );

    validateUserOrganization(
            deletedBy,
            orgId
    );

    InternalDocument doc =
            getByIdForOrg(
                    id,
                    orgId
            );

    Organization organization =
            doc.getOrganization();

    String title =
            doc.getTitle();

    /*
     * Delete the actual entity rather than deleting directly
     * by ID. This ensures the authorization check above is
     * completed against the loaded entity.
     */

    docRepo.delete(
            doc
    );

    /*
     * Flush so database-level failures occur inside this
     * transaction before the method completes.
     */

    docRepo.flush();

    /*
     * Audit the successful deletion.
     */

    try {

        auditService.log(
                organization,
                deletedBy,
                "INTERNAL_DOCUMENT_DELETED",
                "INTERNAL_DOCUMENT",
                String.valueOf(id),
                "Deleted internal document \"" +
                        title +
                        "\"",
                null,
                null,
                "Documents & KYC"
        );

    } catch (RuntimeException e) {

        log.error(
                "Document deletion audit failed. " +
                        "orgId={}, documentId={}",
                orgId,
                id,
                e
        );

        throw new IllegalStateException(
                "The document could not be securely deleted.",
                e
        );
    }

    log.info(
            "Internal document deleted successfully. " +
                    "orgId={}, documentId={}",
            orgId,
            id
    );
}

/*
 * ================================================================
 * ORGANIZATION VALIDATION
 * ================================================================
 */

private void validateOrganization(
        Organization org
) {

    if (org == null) {

        throw new IllegalArgumentException(
                "Organization is required."
        );
    }

    if (org.getId() == null) {

        throw new IllegalArgumentException(
                "Organization ID is required."
        );
    }
}

/*
 * ================================================================
 * UPLOADER ORGANIZATION VALIDATION
 * ================================================================
 */

private void validateUploaderOrganization(
        Organization org,
        User uploadedBy
) {

    if (uploadedBy == null) {
        return;
    }

    if (
            uploadedBy.getOrganization() == null
                    || uploadedBy.getOrganization().getId() == null
    ) {

        throw new IllegalArgumentException(
                "Uploader organization could not be determined."
        );
    }

    if (
            !uploadedBy.getOrganization()
                    .getId()
                    .equals(
                            org.getId()
                    )
    ) {

        throw new SecurityException(
                "You are not authorized to upload documents for this organization."
        );
    }
}

/*
 * ================================================================
 * USER ORGANIZATION VALIDATION FOR DELETE
 * ================================================================
 */

private void validateUserOrganization(
        User user,
        Long orgId
) {

    if (user == null) {
        return;
    }

    if (
            user.getOrganization() == null
                    || user.getOrganization().getId() == null
    ) {

        throw new SecurityException(
                "User organization could not be determined."
        );
    }

    if (
            !user.getOrganization()
                    .getId()
                    .equals(orgId)
    ) {

        throw new SecurityException(
                "You are not authorized to modify documents for this organization."
        );
    }
}

/*
 * ================================================================
 * FILE VALIDATION
 * ================================================================
 */

private void validateFile(
        MultipartFile file
) {

    if (file == null) {

        throw new IllegalArgumentException(
                "No file was received."
        );
    }

    if (file.isEmpty()) {

        throw new IllegalArgumentException(
                "The uploaded file is empty."
        );
    }

    if (file.getSize() <= 0) {

        throw new IllegalArgumentException(
                "The uploaded file is empty."
        );
    }

    if (file.getSize() > MAX_FILE_BYTES) {

        throw new IllegalArgumentException(
                "The uploaded file exceeds the maximum allowed size of 20 MB."
        );
    }

    String contentType =
            normalizeContentType(
                    file.getContentType()
            );

    if (contentType == null) {

        throw new IllegalArgumentException(
                "The uploaded file type could not be determined."
        );
    }

    if (!ALLOWED_TYPES.contains(contentType)) {

        throw new IllegalArgumentException(
                "Unsupported file type."
        );
    }
}

/*
 * ================================================================
 * CONTENT SIGNATURE VALIDATION
 * ================================================================
 *
 * Browser MIME types are not trusted.
 *
 * Supported signatures:
 *
 * PDF      -> %PDF
 * JPEG     -> FF D8 FF
 * PNG      -> PNG signature
 * WEBP     -> RIFF....WEBP
 * DOC/XLS  -> OLE Compound File
 * DOCX/XLSX-> ZIP signature
 *
 * Plain text and CSV are intentionally not signature-enforced
 * because those formats do not have a universal binary magic
 * number.
 */

private void validateFileSignature(
        byte[] data,
        String contentType
) {

    if (data == null || data.length == 0) {

        throw new IllegalArgumentException(
                "The uploaded file is empty."
        );
    }

    boolean valid;

    switch (contentType) {

        case "application/pdf" ->
                valid = startsWith(
                        data,
                        new byte[]{
                                0x25,
                                0x50,
                                0x44,
                                0x46
                        }
                );

        case "image/jpeg",
             "image/jpg" ->
                valid = startsWith(
                        data,
                        new byte[]{
                                (byte) 0xFF,
                                (byte) 0xD8,
                                (byte) 0xFF
                        }
                );

        case "image/png" ->
                valid = startsWith(
                        data,
                        new byte[]{
                                (byte) 0x89,
                                0x50,
                                0x4E,
                                0x47,
                                0x0D,
                                0x0A,
                                0x1A,
                                0x0A
                        }
                );

        case "image/webp" ->
                valid =
                        startsWith(
                                data,
                                new byte[]{
                                        0x52,
                                        0x49,
                                        0x46,
                                        0x46
                                }
                        )
                                && containsAscii(
                                data,
                                8,
                                12,
                                "WEBP"
                        );

        case "application/msword",
             "application/vnd.ms-excel" ->
                valid = startsWith(
                        data,
                        new byte[]{
                                (byte) 0xD0,
                                (byte) 0xCF,
                                0x11,
                                (byte) 0xE0,
                                (byte) 0xA1,
                                (byte) 0xB1,
                                0x1A,
                                (byte) 0xE1
                        }
                );

        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
             "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                valid = startsWith(
                        data,
                        new byte[]{
                                0x50,
                                0x4B,
                                0x03,
                                0x04
                        }
                );

        case "text/plain",
             "text/csv" ->
                valid = isReasonableTextFile(
                        data
                );

        default ->
                valid = false;
    }

    if (!valid) {

        throw new IllegalArgumentException(
                "The uploaded file content does not match its declared file type."
        );
    }
}

/*
 * ================================================================
 * TEXT FILE VALIDATION
 * ================================================================
 */

private boolean isReasonableTextFile(
        byte[] data
) {

    /*
     * Reject NUL bytes. They are a strong indication that the
     * file is binary rather than normal text/CSV.
     */

    for (byte value : data) {

        if (value == 0) {

            return false;
        }
    }

    return true;
}

/*
 * ================================================================
 * BYTE SIGNATURE HELPERS
 * ================================================================
 */

private boolean startsWith(
        byte[] data,
        byte[] signature
) {

    if (data.length < signature.length) {
        return false;
    }

    for (int i = 0; i < signature.length; i++) {

        if (data[i] != signature[i]) {

            return false;
        }
    }

    return true;
}

private boolean containsAscii(
        byte[] data,
        int start,
        int end,
        String expected
) {

    if (
            start < 0
                    || end > data.length
                    || expected == null
    ) {

        return false;
    }

    if (end - start < expected.length()) {

        return false;
    }

    for (
            int i = start;
            i <= end - expected.length();
            i++
    ) {

        boolean matches = true;

        for (
                int j = 0;
                j < expected.length();
                j++
        ) {

            if (
                    data[i + j]
                            != (byte) expected.charAt(j)
            ) {

                matches = false;
                break;
            }
        }

        if (matches) {

            return true;
        }
    }

    return false;
}

/*
 * ================================================================
 * CONTENT TYPE NORMALIZATION
 * ================================================================
 */

private String normalizeContentType(
        String contentType
) {

    if (
            contentType == null
                    || contentType.isBlank()
    ) {

        return null;
    }

    String normalized =
            contentType
                    .trim()
                    .toLowerCase(
                            Locale.ROOT
                    );

    /*
     * Normalize common aliases.
     */

    if ("image/jpg".equals(normalized)) {

        return "image/jpeg";
    }

    return normalized;
}

/*
 * ================================================================
 * CATEGORY NORMALIZATION
 * ================================================================
 */

private String normalizeCategory(
        String category
) {

    String normalized =
            normalizeCategoryOrNull(
                    category
            );

    return normalized != null
            ? normalized
            : DEFAULT_CATEGORY;
}

private String normalizeCategoryOrNull(
        String category
) {

    if (
            category == null
                    || category.isBlank()
    ) {

        return null;
    }

    String normalized =
            category
                    .trim()
                    .toUpperCase(
                            Locale.ROOT
                    );

    if (!CATEGORIES.contains(normalized)) {

        throw new IllegalArgumentException(
                "Invalid document category."
        );
    }

    return normalized;
}

/*
 * ================================================================
 * TITLE NORMALIZATION
 * ================================================================
 */

private String resolveTitle(
        String title,
        String originalFileName
) {

    String providedTitle =
            sanitizeText(
                    title
            );

    if (
            providedTitle != null
                    && !providedTitle.isBlank()
    ) {

        return truncate(
                providedTitle,
                MAX_TITLE_LENGTH
        );
    }

    String baseName =
            originalFileName;

    int extensionIndex =
            baseName.lastIndexOf('.');

    if (extensionIndex > 0) {

        baseName =
                baseName.substring(
                        0,
                        extensionIndex
                );
    }

    baseName =
            sanitizeText(
                    baseName
            );

    if (
            baseName == null
                    || baseName.isBlank()
    ) {

        baseName = "Document";
    }

    String generatedTitle =
            baseName
                    + " ("
                    + UUID.randomUUID()
                    .toString()
                    .substring(
                            0,
                            8
                    )
                    + ")";

    return truncate(
            generatedTitle,
            MAX_TITLE_LENGTH
    );
}

/*
 * ================================================================
 * DESCRIPTION NORMALIZATION
 * ================================================================
 */

private String normalizeDescription(
        String description
) {

    String normalized =
            sanitizeText(
                    description
            );

    if (
            normalized == null
                    || normalized.isBlank()
    ) {

        return null;
    }

    return truncate(
            normalized,
            MAX_DESCRIPTION_LENGTH
    );
}

/*
 * ================================================================
 * FILENAME SANITIZATION
 * ================================================================
 */

private String sanitizeFileName(
        String fileName
) {

    if (
            fileName == null
                    || fileName.isBlank()
    ) {

        return null;
    }

    String sanitized =
            fileName
                    .replace(
                            '\\',
                            '/'
                    );

    /*
     * Remove directory traversal components.
     */

    int lastSlash =
            sanitized.lastIndexOf('/');

    if (lastSlash >= 0) {

        sanitized =
                sanitized.substring(
                        lastSlash + 1
                );
    }

    /*
     * Remove control characters.
     */

    sanitized =
            sanitized.replaceAll(
                    "[\\p{Cntrl}]",
                    ""
            );

    /*
     * Remove characters commonly dangerous for generated
     * filesystem paths.
     */

    sanitized =
            sanitized.replaceAll(
                    "[<>:\"/\\\\|?*]",
                    "_"
            );

    sanitized =
            sanitized.trim();

    if (sanitized.isBlank()) {

        return null;
    }

    return truncate(
            sanitized,
            MAX_FILENAME_LENGTH
    );
}

/*
 * ================================================================
 * TEXT SANITIZATION
 * ================================================================
 */

private String sanitizeText(
        String value
) {

    if (value == null) {

        return null;
    }

    String normalized =
            value
                    .replace(
                            "\u0000",
                            ""
                    )
                    .trim();

    return normalized.isBlank()
            ? null
            : normalized;
}

/*
 * ================================================================
 * TRUNCATION
 * ================================================================
 */

private String truncate(
        String value,
        int maxLength
) {

    if (value == null) {

        return null;
    }

    if (value.length() <= maxLength) {

        return value;
    }

    return value.substring(
            0,
            maxLength
    );
}

/*
 * ================================================================
 * SHA-256
 * ================================================================
 */

private String calculateSha256(
        byte[] data
) {

    try {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-256"
                );

        byte[] hash =
                digest.digest(
                        data
                );

        return HexFormat.of()
                .formatHex(
                        hash
                );

    } catch (NoSuchAlgorithmException e) {

        /*
         * SHA-256 is required by every standard Java runtime.
         * Treat absence as a fatal application configuration
         * problem.
         */

        throw new IllegalStateException(
                "SHA-256 is not available in this Java runtime.",
                e
        );
    }
}

/*
 * ================================================================
 * EXTENSION
 * ================================================================
 */

private String extensionForContentType(
        String contentType
) {

    if (contentType == null) {

        return "";
    }

    return switch (contentType) {

        case "application/pdf" ->
                ".pdf";

        case "image/jpeg",
             "image/jpg" ->
                ".jpg";

        case "image/png" ->
                ".png";

        case "image/webp" ->
                ".webp";

        case "application/msword" ->
                ".doc";

        case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                ".docx";

        case "application/vnd.ms-excel" ->
                ".xls";

        case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ->
                ".xlsx";

        case "text/csv" ->
                ".csv";

        case "text/plain" ->
                ".txt";

        default ->
                "";
    };
}

/*
 * ================================================================
 * ID VALIDATION
 * ================================================================
 */

private void validateDocumentId(
        Long id
) {

    if (id == null || id <= 0) {

        throw new IllegalArgumentException(
                "Document ID is required."
        );
    }
}

private void validateOrganizationId(
        Long orgId
) {

    if (orgId == null || orgId <= 0) {

        throw new IllegalArgumentException(
                "Organization ID is required."
        );
    }
}


}
