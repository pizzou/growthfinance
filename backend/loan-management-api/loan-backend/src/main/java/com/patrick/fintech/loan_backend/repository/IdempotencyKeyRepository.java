package com.patrick.fintech.loan_backend.repository;
import com.patrick.fintech.loan_backend.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey,Long> {
    Optional<IdempotencyKey> findByKeyAndOrganization(String key,Organization organization);
}
