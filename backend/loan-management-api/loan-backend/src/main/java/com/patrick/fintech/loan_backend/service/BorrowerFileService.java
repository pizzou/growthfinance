package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Borrower;
import com.patrick.fintech.loan_backend.model.BorrowerFile;
import com.patrick.fintech.loan_backend.model.DocumentType;
import com.patrick.fintech.loan_backend.model.VerificationStatus;
import com.patrick.fintech.loan_backend.repository.BorrowerFileRepository;
import com.patrick.fintech.loan_backend.repository.BorrowerRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BorrowerFileService {

    private final BorrowerFileRepository fileRepository;
    private final BorrowerRepository borrowerRepository;

    public BorrowerFileService(BorrowerFileRepository fileRepository,
                               BorrowerRepository borrowerRepository) {
        this.fileRepository = fileRepository;
        this.borrowerRepository = borrowerRepository;
    }

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;

    /**
     * Validate uploaded file.
     */
    private void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file was received.");
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RuntimeException("Maximum file size is 8MB.");
        }

        String contentType = file.getContentType();

        if (contentType == null ||
                !ALLOWED_TYPES.contains(contentType.toLowerCase())) {

            throw new RuntimeException(
                    "Unsupported file type. Allowed: PDF, JPG, PNG, WEBP."
            );
        }
    }

    /**
     * Upload borrower document.
     */
    public BorrowerFile upload(Long borrowerId,
                               MultipartFile file,
                               DocumentType documentType,
                               boolean uploadedByApplicant)
            throws IOException {

        validate(file);

        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() ->
                        new RuntimeException("Borrower not found: " + borrowerId));

        BorrowerFile borrowerFile = new BorrowerFile();

        borrowerFile.setBorrower(borrower);

        borrowerFile.setFileName(file.getOriginalFilename());
        borrowerFile.setFileType(file.getContentType());
        borrowerFile.setFileSize(file.getSize());
        borrowerFile.setData(file.getBytes());

        borrowerFile.setDocumentType(
                documentType != null
                        ? documentType
                        : DocumentType.OTHER
        );

        borrowerFile.setUploadedByApplicant(uploadedByApplicant);

        borrowerFile.setVerificationStatus(VerificationStatus.PENDING);

        return fileRepository.save(borrowerFile);
    }

    /**
     * Default upload.
     */
    public BorrowerFile upload(Long borrowerId,
                               MultipartFile file)
            throws IOException {

        return upload(
                borrowerId,
                file,
                DocumentType.OTHER,
                false
        );
    }

    /**
     * Get all files for borrower.
     */
    public List<BorrowerFile> getByBorrower(Long borrowerId) {
        return fileRepository.findByBorrowerId(borrowerId);
    }

    /**
     * Metadata only.
     */
    public List<BorrowerFile> getByBorrowerMetadataOnly(Long borrowerId) {

        List<BorrowerFile> files =
                fileRepository.findByBorrowerId(borrowerId);

        files.forEach(f -> f.setData(null));

        return files;
    }

    /**
     * Get file.
     */
    public BorrowerFile getById(Long fileId) {

        return fileRepository.findById(fileId)
                .orElseThrow(() ->
                        new RuntimeException("File not found: " + fileId));
    }

    /**
     * Get file ensuring organization ownership.
     */
    public BorrowerFile getByIdForOrg(Long fileId, Long orgId) {

        BorrowerFile file = getById(fileId);

        if (file.getBorrower() == null
                || file.getBorrower().getOrganization() == null
                || !file.getBorrower()
                        .getOrganization()
                        .getId()
                        .equals(orgId)) {

            throw new RuntimeException("File not found.");
        }

        return file;
    }

    /**
     * Verify document.
     */
    public BorrowerFile verify(Long fileId,
                               Long orgId,
                               VerificationStatus status,
                               String comment,
                               String officerName) {

        BorrowerFile file = getByIdForOrg(fileId, orgId);

        file.setVerificationStatus(status);
        file.setOfficerComment(comment);
        file.setVerifiedByName(officerName);
        file.setVerifiedAt(LocalDateTime.now());

        return fileRepository.save(file);
    }

    /**
     * Delete file.
     */
    public void delete(Long fileId) {
        fileRepository.deleteById(fileId);
    }

    /**
     * Missing required document types.
     */
    public List<DocumentType> getMissingDocumentTypes(
            Long borrowerId,
            List<DocumentType> required) {

        if (required == null || required.isEmpty()) {
            return List.of();
        }

        Set<DocumentType> uploaded =
                fileRepository.findByBorrowerId(borrowerId)
                        .stream()
                        .map(BorrowerFile::getDocumentType)
                        .collect(Collectors.toSet());

        return required.stream()
                .filter(doc -> !uploaded.contains(doc))
                .toList();
    }

    /**
     * Required documents not yet verified.
     */
    public List<DocumentType> getUnverifiedDocumentTypes(
            Long borrowerId,
            List<DocumentType> required) {

        if (required == null || required.isEmpty()) {
            return List.of();
        }

        Set<DocumentType> verified =
                fileRepository.findByBorrowerId(borrowerId)
                        .stream()
                        .filter(f -> f.getVerificationStatus() == VerificationStatus.VERIFIED)
                        .map(BorrowerFile::getDocumentType)
                        .collect(Collectors.toSet());

        return required.stream()
                .filter(doc -> !verified.contains(doc))
                .toList();
    }
}