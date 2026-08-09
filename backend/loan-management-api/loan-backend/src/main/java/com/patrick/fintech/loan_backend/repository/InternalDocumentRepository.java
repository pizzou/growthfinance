package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.InternalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InternalDocumentRepository extends JpaRepository<InternalDocument, Long> {

    // Explicit projection query (excludes the `data` byte[]) so listing documents doesn't pull
    // every file's full bytes into memory just to render a list — same reasoning as
    // BorrowerFileRepository's own list query.
    @Query("select new com.patrick.fintech.loan_backend.repository.InternalDocumentRepository$Summary(" +
           "d.id, d.title, d.category, d.description, d.fileName, d.fileType, d.fileSize, " +
           "d.uploadedBy.name, d.createdAt) " +
           "from InternalDocument d where d.organization.id = :orgId order by d.createdAt desc")
    List<Summary> findSummariesByOrg(@Param("orgId") Long orgId);

    @Query("select new com.patrick.fintech.loan_backend.repository.InternalDocumentRepository$Summary(" +
           "d.id, d.title, d.category, d.description, d.fileName, d.fileType, d.fileSize, " +
           "d.uploadedBy.name, d.createdAt) " +
           "from InternalDocument d where d.organization.id = :orgId and d.category = :category order by d.createdAt desc")
    List<Summary> findSummariesByOrgAndCategory(@Param("orgId") Long orgId, @Param("category") String category);

    record Summary(Long id, String title, String category, String description, String fileName,
                    String fileType, Long fileSize, String uploadedByName, java.time.LocalDateTime createdAt) {}
}
