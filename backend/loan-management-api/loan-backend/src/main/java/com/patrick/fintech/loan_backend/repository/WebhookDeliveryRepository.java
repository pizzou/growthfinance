package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.WebhookDelivery;
import com.patrick.fintech.loan_backend.model.WebhookEndpoint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryRepository
        extends JpaRepository<WebhookDelivery, Long> {


    // ============================================================
    // BY ORGANIZATION
    // ============================================================

    List<WebhookDelivery>
    findByOrganizationOrderByCreatedAtDesc(
            Organization organization
    );


    Page<WebhookDelivery>
    findByOrganizationOrderByCreatedAtDesc(
            Organization organization,
            Pageable pageable
    );


    // ============================================================
    // BY WEBHOOK ENDPOINT
    // ============================================================

    List<WebhookDelivery>
    findByWebhookEndpointOrderByCreatedAtDesc(
            WebhookEndpoint webhookEndpoint
    );


    Page<WebhookDelivery>
    findByWebhookEndpointOrderByCreatedAtDesc(
            WebhookEndpoint webhookEndpoint,
            Pageable pageable
    );


    // ============================================================
    // BY EVENT TYPE
    // ============================================================

    List<WebhookDelivery>
    findByOrganizationAndEventTypeOrderByCreatedAtDesc(
            Organization organization,
            String eventType
    );


    // ============================================================
    // COUNTS
    // ============================================================

    long countByOrganization(
            Organization organization
    );


    long countByOrganizationAndStatus(
            Organization organization,
            String status
    );
}
