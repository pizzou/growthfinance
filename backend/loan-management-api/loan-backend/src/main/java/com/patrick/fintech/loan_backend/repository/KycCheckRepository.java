package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository
public interface KycCheckRepository extends JpaRepository<KycCheck,Long> {
    List<KycCheck> findByBorrower_Id(Long borrowerId);
    List<KycCheck> findByOrganization_Id(Long orgId);
    Optional<KycCheck> findFirstByBorrower_IdAndCheckTypeOrderByCreatedAtDesc(Long borrowerId,KycCheck.CheckType checkType);
    List<KycCheck> findByOrganization_IdAndResult(Long orgId,KycCheck.CheckResult result);
}
