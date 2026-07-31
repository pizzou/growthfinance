package com.patrick.fintech.loan_backend.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String name;
    private String email;
    private String role;
    private Long organizationId;
    private String organizationName;
}
