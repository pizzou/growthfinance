package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WebhookRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByOrganizationAndActiveTrue(Organization organization);
    List<WebhookEndpoint> findByOrganization(Organization organization);
}
