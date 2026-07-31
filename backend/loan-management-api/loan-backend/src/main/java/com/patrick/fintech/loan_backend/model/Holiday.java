package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "holidays")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Holiday {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Organization organization;

    private LocalDate holidayDate;
    private String name;
    private Boolean recurringAnnually = false;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}