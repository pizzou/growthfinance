package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.CollectionAction;
import com.patrick.fintech.loan_backend.model.CollectionCase;
import com.patrick.fintech.loan_backend.service.CollectionsService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','LOAN_OFFICER','ACCOUNTANT','COLLECTIONS_OFFICER')")
public class CollectionsController {

    private final CollectionsService collectionsService;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<CollectionCase>>> queue(
            @RequestParam(required = false) CollectionCase.CollectionBucket bucket,
            @RequestParam(required = false) CollectionCase.CollectionStatus status,
            @RequestParam(required = false) Long agentId) {
        return ResponseEntity.ok(ApiResponse.ok(
            collectionsService.getQueue(currentUserUtil.getCurrentOrganizationId(), bucket, status, agentId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(collectionsService.getStats(currentUserUtil.getCurrentOrganizationId())));
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<ApiResponse<CollectionCase>> getCase(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(collectionsService.getCase(id)));
    }

    @GetMapping("/cases/{id}/actions")
    public ResponseEntity<ApiResponse<List<CollectionAction>>> actions(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(collectionsService.getActions(id)));
    }

    @PostMapping("/cases/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<CollectionCase>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long agentId = Long.valueOf(body.get("agentId").toString());
        return ResponseEntity.ok(ApiResponse.ok(
            collectionsService.assignAgent(id, agentId, currentUserUtil.getCurrentUser().getName())));
    }

    @PostMapping("/cases/{id}/actions")
    public ResponseEntity<ApiResponse<CollectionAction>> logAction(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        CollectionAction.ActionType type = CollectionAction.ActionType.valueOf(body.get("actionType").toString());
        String notes = (String) body.get("notes");
        String outcome = (String) body.get("outcome");
        LocalDate promiseDate = body.get("promiseDate") != null ? LocalDate.parse(body.get("promiseDate").toString()) : null;
        Double promiseAmount = body.get("promiseAmount") != null ? Double.valueOf(body.get("promiseAmount").toString()) : null;

        CollectionAction action = collectionsService.logAction(
            id, type, notes, currentUserUtil.getCurrentUser().getName(), outcome, promiseDate, promiseAmount);
        return ResponseEntity.ok(ApiResponse.ok("Action logged", action));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<String>> manualSync() {
        int touched = collectionsService.syncCasesFromOverdueLoans();
        return ResponseEntity.ok(ApiResponse.ok("Collections queue synced: " + touched + " case(s) touched"));
    }
}
