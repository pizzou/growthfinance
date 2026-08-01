package com.patrick.fintech.loan_backend.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BorrowerRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @Email   private String email;
    private String phone;
    @NotBlank
    private String alternatePhone;
       @NotBlank(message = "National ID is required")
@Pattern(
    regexp = "^\\d{16}$",
    message = "National ID must contain exactly 16 digits"
)
    private String nationalId;
    private String passportNumber;
    private String taxIdentificationNumber;
    private String dateOfBirth;
    @NotBlank
    private String gender;
    @NotBlank
    private String maritalStatus;
    private String nationality;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String stateProvince;
    private String postalCode;
    private String country;
    private String employerName;
    private String employmentType;
    private String jobTitle;
    private Double monthlyIncome;
    private Double monthlyExpenses;
    private Double netWorth;
    private Integer creditScore;
    private String creditBureau;
    private String bankName;
    private String bankAccountNumber;
    private String bankBranch;
}