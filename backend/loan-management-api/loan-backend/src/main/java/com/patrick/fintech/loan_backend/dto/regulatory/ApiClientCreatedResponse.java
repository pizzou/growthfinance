package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.Builder;
import lombok.Data;

/** Returned exactly once, at creation time — the only moment the raw API key is ever visible. */
@Data @Builder
public class ApiClientCreatedResponse {
    private ApiClientResponse client;
    private String apiKey;   // raw key — show once, tell the admin to copy it now
}
