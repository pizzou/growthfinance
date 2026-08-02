package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.ChartOfAccount;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, Long> {

    
    List<ChartOfAccount> findByOrganization_IdOrderByCodeAsc(Long orgId);

   
    List<ChartOfAccount> findByOrganization_IdAndActiveTrueOrderByCodeAsc(Long orgId);

   
    Optional<ChartOfAccount> findByOrganization_IdAndCode(
        Long orgId,
        String code
    );

    
    Optional<ChartOfAccount> findByIdAndOrganization_Id(
        Long id,
        Long orgId
    );

    
    boolean existsByOrganization_IdAndCode(
        Long orgId,
        String code
    );
}