package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A message submitted through the public website's contact form. Previously these only ever
 * existed as a one-shot in-app Notification to admins/managers — nothing was ever saved, so
 * there was no way to browse past messages, mark them handled, or find one again after the
 * notification list moved on. This is the permanent record; the Notification is just the ping.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "contact_messages", indexes = @Index(name = "idx_contact_messages_org", columnList = "organization_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ContactMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String name;
    private String email;
    private String phone;
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Builder.Default
    private boolean read = false;

    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        read = false;
    }
}
