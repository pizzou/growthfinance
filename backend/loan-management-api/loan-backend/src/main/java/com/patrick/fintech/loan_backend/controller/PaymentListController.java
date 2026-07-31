package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Organization;
import com.patrick.fintech.loan_backend.model.Payment;
import com.patrick.fintech.loan_backend.repository.PaymentRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Org-wide payment views for the dashboard Payments page — separate from
 * PaymentController (which is loan-scoped, /api/loans/{id}/payments, for
 * recording/viewing one loan's schedule). This is what actually backs the
 * "Payments" page in the sidebar: every payment across every loan, and the
 * overdue subset. These endpoints didn't previously exist, even though the
 * frontend was already calling them — the page was silently getting 404s
 * and rendering as empty.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentListController {

    private final PaymentRepository paymentRepo;
    private final CurrentUserUtil   currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Payment>>> getAll() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        List<Payment> payments = paymentRepo.findByLoan_Organization_Id(orgId);
        payments.sort(Comparator.comparing(Payment::getDueDate,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return ResponseEntity.ok(ApiResponse.ok(payments));
    }

    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<Payment>>> getOverdue() {
        Organization org = currentUserUtil.getCurrentUser().getOrganization();
        List<Payment> overdue = paymentRepo.findByOrganization_IdAndPaidFalseAndDueDateBefore(
            org.getId(), LocalDate.now());
        overdue.sort(Comparator.comparing(Payment::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        return ResponseEntity.ok(ApiResponse.ok(overdue));
    }
}
