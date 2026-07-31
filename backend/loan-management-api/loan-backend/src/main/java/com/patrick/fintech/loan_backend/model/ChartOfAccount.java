package com.patrick.fintech.loan_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity
@Table(name = "chart_of_accounts", indexes = @Index(name = "idx_coa_org", columnList = "organization_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChartOfAccount {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    private String code;   // e.g. "1000"
    private String name;   // e.g. "Cash and Bank"

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    private NormalBalance normalBalance;

    @Builder.Default
    private Boolean active = true;

    public enum AccountType { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }
    public enum NormalBalance { DEBIT, CREDIT }
}
