package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByOrganization(Organization organization);
    long countByOrganization(Organization organization);
}
