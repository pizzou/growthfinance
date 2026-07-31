package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {
    List<ChartOfAccount> findByOrganization_IdOrderByCodeAsc(Long orgId);
    Optional<ChartOfAccount> findByOrganization_IdAndCode(Long orgId, String code);
    Optional<ChartOfAccount> findByIdAndOrganization_Id(Long id, Long orgId);
    boolean existsByOrganization_IdAndCode(Long orgId, String code);
}
