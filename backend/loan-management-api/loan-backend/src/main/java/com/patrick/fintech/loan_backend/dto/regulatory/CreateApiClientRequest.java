package com.patrick.fintech.loan_backend.dto.regulatory;

import com.patrick.fintech.loan_backend.model.RegulatoryApiClient;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateApiClientRequest {
    private String name;
    private RegulatoryApiClient.ClientType clientType;
    private String contactEmail;
    private String description;
    private LocalDateTime expiresAt;
}
