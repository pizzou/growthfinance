package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {

    private Long paymentId;

    private LocalDate paymentDate;

    private Double amount;

    private String method;

    private String status;

}