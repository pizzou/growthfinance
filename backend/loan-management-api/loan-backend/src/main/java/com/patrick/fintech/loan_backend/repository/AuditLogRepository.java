package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.AuditLog;
import com.patrick.fintech.loan_backend.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @org.springframework.data.jpa.repository.Query(
        "SELECT a FROM AuditLog a LEFT JOIN FETCH a.user WHERE a.organization = :org ORDER BY a.timestamp DESC")
    Page<AuditLog> findByOrganizationOrderByTimestampDesc(@org.springframework.data.repository.query.Param("org") Organization organization, Pageable pageable);
    // alias used in AuditController
    default Page<AuditLog> findByInstitutionOrderByTimestampDesc(Organization org, Pageable p) {
        return findByOrganizationOrderByTimestampDesc(org, p);
    }

    java.util.Optional<AuditLog> findTopByOrderByIdDesc();
    java.util.List<AuditLog> findAllByOrderByIdAsc();
}
