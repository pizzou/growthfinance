package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.*;
import com.patrick.fintech.loan_backend.repository.*;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
@RestController @RequestMapping("/api/privacy") @RequiredArgsConstructor
public class DataPrivacyController {
    private final BorrowerRepository borrowerRepo;
    private final LoanRepository loanRepo;
    private final KycCheckRepository kycRepo;
    private final AuditService auditService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping("/borrowers/{id}/export") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> export(@PathVariable Long id) {
        Long orgId=currentUserUtil.getCurrentOrganizationId();
        Borrower b=borrowerRepo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        if(!b.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("exportedAt",LocalDateTime.now().toString());
        out.put("dataSubjectId",id);
        Map<String,Object> personal=new LinkedHashMap<>();
        personal.put("firstName",b.getFirstName()); personal.put("lastName",b.getLastName());
        personal.put("email",b.getEmail()); personal.put("phone",b.getPhone());
        personal.put("nationalId",b.getNationalId()); personal.put("dateOfBirth",String.valueOf(b.getDateOfBirth()));
        personal.put("address",b.getAddressLine1()+" "+b.getCity()+" "+b.getCountry());
        personal.put("employer",b.getEmployerName()); personal.put("kycStatus",b.getKycStatus());
        out.put("personalData",personal);
        out.put("loans",loanRepo.findByBorrowerIdAndOrganizationId(id,orgId).stream().map(l->Map.of(
            "reference",l.getReferenceNumber(),"status",l.getStatus(),"amount",l.getAmount()!=null?l.getAmount():0)).toList());
        out.put("kycChecks",kycRepo.findByBorrower_Id(id).stream().map(k->Map.of(
            "checkType",k.getCheckType(),"result",k.getResult(),"createdAt",String.valueOf(k.getCreatedAt()))).toList());
        out.put("retentionPolicy","7 years post loan closure (regulatory requirement)");
        return ResponseEntity.ok(ApiResponse.ok("Data export complete",out));
    }

    @DeleteMapping("/borrowers/{id}/erase") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public ResponseEntity<ApiResponse<Map<String,Object>>> erase(@PathVariable Long id) {
        Long orgId=currentUserUtil.getCurrentOrganizationId();
        Borrower b=borrowerRepo.findById(id).orElseThrow(()->new RuntimeException("Not found"));
        if(!b.getOrganization().getId().equals(orgId)) throw new RuntimeException("Access denied");
        long active=loanRepo.findByBorrowerIdAndOrganizationId(id,orgId).stream()
            .filter(l->l.getStatus()==LoanStatus.ACTIVE||l.getStatus()==LoanStatus.OVERDUE).count();
        if(active>0) return ResponseEntity.badRequest().body(ApiResponse.ok(
            "Erasure blocked: "+active+" active loan(s) — data retained for regulatory compliance",
            Map.of("erased",false,"activeLoans",active)));
        String anon="[ERASED]";
        b.setFirstName(anon); b.setLastName(anon); b.setEmail("erased-"+id+"@deleted.invalid");
        b.setPhone(anon); b.setNationalId(anon); b.setPassportNumber(null);
        b.setAddressLine1(anon); b.setCity(anon); b.setEmployerName(anon);
        b.setBankAccountNumber(anon); b.setKycStatus("ERASED");
        borrowerRepo.save(b);
        auditService.log(b.getOrganization(), currentUserUtil.getCurrentUser(),
            "GDPR_ERASURE", "BORROWER", id.toString(), "Personal data anonymized per data protection request");
        return ResponseEntity.ok(ApiResponse.ok("Personal data anonymized",
            Map.of("erased",true,"financialRecordsRetained",true)));
    }
}
