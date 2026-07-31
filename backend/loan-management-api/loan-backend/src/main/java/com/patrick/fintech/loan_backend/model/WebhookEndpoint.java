package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-organization webhook subscriptions — so external systems
 * (CRBs, core banking, ERPs) receive real-time events.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "webhook_endpoints")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookEndpoint {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String url;

    private String description;
    private String secret;       // HMAC-SHA256 signing secret
    private boolean active;

    @ElementCollection
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Column(name = "event_type")
    private List<String> subscribedEvents;  // e.g. LOAN_APPROVED, PAYMENT_MADE

    private Integer failureCount;
    private LocalDateTime lastDeliveryAt;
    private String        lastDeliveryStatus;
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now(); active = true; failureCount = 0;
    }
}
