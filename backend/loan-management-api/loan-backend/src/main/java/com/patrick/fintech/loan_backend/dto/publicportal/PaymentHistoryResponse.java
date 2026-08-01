package com.patrick.fintech.loan_backend.dto.publicportal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {

    private Long paymentId;

    private LocalDate paymentDate;

    private BigDecimal amount;

    private String method;

    private String status;
}