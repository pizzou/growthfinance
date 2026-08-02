
package com.patrick.fintech.loan_backend.dto.regulatory;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BnrBreakdownRow {


    private String label;

 
    private long count;

  
    private double amount;
}
