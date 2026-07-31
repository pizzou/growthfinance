package com.patrick.fintech.loan_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Data @NoArgsConstructor @AllArgsConstructor
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;        // ADMIN, LOAN_OFFICER, CREDIT_ANALYST, TELLER, MANAGER

    private String description;
}
