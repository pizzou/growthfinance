package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanTimelineResponse {

    private String action;

    private String description;

    private String performedBy;

    private LocalDateTime performedAt;
}
