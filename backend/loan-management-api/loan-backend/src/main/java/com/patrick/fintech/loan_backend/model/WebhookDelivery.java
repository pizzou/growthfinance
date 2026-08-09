package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
        name = "webhook_deliveries",
        indexes = {
                @Index(
                        name = "idx_webhook_delivery_endpoint",
                        columnList = "webhook_endpoint_id"
                ),
                @Index(
                        name = "idx_webhook_delivery_organization",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_webhook_delivery_event",
                        columnList = "event_type"
                ),
                @Index(
                        name = "idx_webhook_delivery_created",
                        columnList = "created_at"
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "webhook_endpoint_id",
            nullable = false
    )
    private WebhookEndpoint webhookEndpoint;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;


    @Column(
            name = "event_type",
            nullable = false,
            length = 100
    )
    private String eventType;


    @Column(
            name = "payload",
            columnDefinition = "TEXT"
    )
    private String payload;


    /**
     * URL where the webhook was delivered.
     *
     * Stored separately so the delivery history remains useful
     * even if the endpoint URL is later changed.
     */
    @Column(
            name = "endpoint_url",
            columnDefinition = "TEXT"
    )
    private String endpointUrl;


    // ============================================================
    // DELIVERY RESULT
    // ============================================================

    /**
     * SUCCESS or FAILED.
     */
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private String status;


    /**
     * HTTP response status returned by the receiving endpoint.
     *
     * Example:
     *
     * 200
     * 201
     * 400
     * 500
     */
    @Column(name = "http_status")
    private Integer httpStatus;


    /**
     * Response body returned by the external webhook endpoint.
     */
    @Column(
            name = "response_body",
            columnDefinition = "TEXT"
    )
    private String responseBody;


    /**
     * Error message when delivery fails.
     */
    @Column(
            name = "error_message",
            columnDefinition = "TEXT"
    )
    private String errorMessage;


    // ============================================================
    // ATTEMPTS
    // ============================================================

    /**
     * Number of delivery attempts.
     *
     * Initially 1.
     */
    @Column(
            name = "attempt_count",
            nullable = false
    )
    private Integer attemptCount;


    // ============================================================
    // TIMESTAMPS
    // ============================================================

    /**
     * When this webhook delivery record was created.
     */
    @Column(
            name = "created_at",
            nullable = false
    )
    private LocalDateTime createdAt;


    /**
     * When the delivery was completed.
     */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;


    // ============================================================
    // PRE-PERSIST
    // ============================================================

    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (attemptCount == null) {
            attemptCount = 1;
        }

        if (status == null || status.isBlank()) {
            status = "PENDING";
        }
    }
}
