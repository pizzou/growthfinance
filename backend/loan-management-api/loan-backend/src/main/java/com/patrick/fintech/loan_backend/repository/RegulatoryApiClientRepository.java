package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;


public interface RegulatoryApiClientRepository
        extends JpaRepository<RegulatoryApiClient, Long> {

    Optional<RegulatoryApiClient> findByKeyPrefix(
            String keyPrefix
    );

    List<RegulatoryApiClient>
    findByOrganization_IdOrderByCreatedAtDesc(
            Long organizationId
    );
}