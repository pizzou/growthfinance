package com.patrick.fintech.loan_backend.controller;
import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.service.BulkDisbursementService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/bulk") @RequiredArgsConstructor @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class BulkController {
    private final BulkDisbursementService bulkService;
    private final CurrentUserUtil currentUserUtil;
    @PostMapping("/disburse")
    public ResponseEntity<ApiResponse<BulkDisbursementService.BulkDisbursementResult>> disburse(@RequestBody Map<String,Object> body) {
        var user=currentUserUtil.getCurrentUser();
        @SuppressWarnings("unchecked") List<Number> ids=(List<Number>)body.get("loanIds");
        return ResponseEntity.ok(ApiResponse.ok(bulkService.disburseAll(ids.stream().map(Number::longValue).toList(),
            user.getOrganization().getId(),user,body.getOrDefault("disbursementMethod","BANK_TRANSFER").toString())));
    }
}
