package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class BulkDisbursementService {
    private final LoanRepository     loanRepo;
    private final AuditService       auditService;
    private final WebhookService     webhookService;
    private final SmsService         smsService;

    @Transactional
    public BulkDisbursementResult disburseAll(List<Long> loanIds,Long orgId,User officer,String method){
        List<DisbursementLine> lines=new ArrayList<>();
        double total=0; int ok=0,fail=0;
        for(Long id:loanIds){
            try{
                Loan loan=loanRepo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
                if(!loan.getOrganization().getId().equals(orgId)){
                    lines.add(DisbursementLine.failed(id,null,"Access denied")); fail++; continue;
                }
                if(loan.getStatus()!=LoanStatus.APPROVED){
                    lines.add(DisbursementLine.failed(id,loan.getReferenceNumber(),"Status is "+loan.getStatus())); fail++; continue;
                }
                loan.setStatus(LoanStatus.ACTIVE);
                loan.setDisbursedAt(LocalDate.now());
                loan.setDisbursedAmount(loan.getAmount());
                loan.setMaturityDate(LocalDate.now().plusMonths(loan.getDurationMonths()));
                loan.setNextDueDate(LocalDate.now().plusMonths(1));
                loanRepo.save(loan);
                total+=loan.getAmount()!=null?loan.getAmount():0;
                ok++; lines.add(DisbursementLine.success(id,loan.getReferenceNumber(),loan.getAmount(),loan.getCurrency()));
                try{smsService.sendLoanApproved(loan);}catch(Exception e){}
                auditService.log(loan.getOrganization(), officer,
                    "BULK_DISBURSEMENT", "LOAN", id.toString(), "Bulk disbursed via "+method);
                webhookService.dispatch(loan.getOrganization(),"LOAN_DISBURSED",loan);
            }catch(Exception e){
                log.warn("Bulk fail for loan {}: {}",id,e.getMessage());
                lines.add(DisbursementLine.failed(id,null,e.getMessage())); fail++;
            }
        }
        log.info("Bulk disbursement: {}/{} succeeded, total {}",ok,loanIds.size(),total);
        return new BulkDisbursementResult(ok,fail,total,method,LocalDateTime.now(),lines);
    }

    public record DisbursementLine(Long loanId,String referenceNumber,boolean success,Double amount,String currency,String errorMessage){
        static DisbursementLine success(Long id,String ref,Double amt,String cur){return new DisbursementLine(id,ref,true,amt,cur,null);}
        static DisbursementLine failed(Long id,String ref,String err){return new DisbursementLine(id,ref,false,null,null,err);}
    }
    public record BulkDisbursementResult(int successCount,int failureCount,double totalAmountDisbursed,String disbursementMethod,LocalDateTime processedAt,List<DisbursementLine> lines){}
}
