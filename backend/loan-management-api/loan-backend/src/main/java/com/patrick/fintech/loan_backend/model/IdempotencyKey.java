package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="idempotency_keys",
    uniqueConstraints=@UniqueConstraint(columnNames={"idempotency_key","organization_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyKey {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="idempotency_key",nullable=false) 
    private String key;
    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="organization_id",nullable=false) 
    private Organization organization;
    private String endpoint;
    private String requestHash;
    @Enumerated(EnumType.STRING) private Status status;
    @Column(columnDefinition="TEXT") private String responseBody;
    private Integer responseStatusCode;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    @PrePersist protected void onCreate(){
        createdAt=LocalDateTime.now();
        if(expiresAt==null) expiresAt=createdAt.plusHours(24);
        if(status==null) status=Status.IN_PROGRESS;
    }
    public enum Status { IN_PROGRESS,COMPLETED,FAILED }
}
