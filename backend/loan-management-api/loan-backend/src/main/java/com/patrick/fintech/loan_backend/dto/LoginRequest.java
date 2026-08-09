package com.patrick.fintech.loan_backend.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String email;
    private String password;
    private String mfaCode;
    private String otp;

}
