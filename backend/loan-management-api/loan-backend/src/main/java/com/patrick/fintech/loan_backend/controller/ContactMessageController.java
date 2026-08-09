package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.ContactMessage;
import com.patrick.fintech.loan_backend.repository.ContactMessageRepository;
import com.patrick.fintech.loan_backend.service.AuditService;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Staff inbox for messages submitted through the public website's contact form. */
@RestController
@RequestMapping("/api/contact-messages")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageRepository contactMessageRepo;
    private final CurrentUserUtil          currentUserUtil;
    private final AuditService             auditService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ContactMessage>>> list() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(contactMessageRepo.findByOrganization_IdOrderByCreatedAtDesc(orgId)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String,Long>>> unreadCount() {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", contactMessageRepo.countByOrganization_IdAndReadFalse(orgId))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<ContactMessage>> markRead(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        ContactMessage msg = contactMessageRepo.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new RuntimeException("Message not found: " + id));
        msg.setRead(true);
        msg.setReadAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.ok(contactMessageRepo.save(msg)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long orgId = currentUserUtil.getCurrentOrganizationId();
        ContactMessage msg = contactMessageRepo.findByIdAndOrganization_Id(id, orgId)
            .orElseThrow(() -> new RuntimeException("Message not found: " + id));
        contactMessageRepo.delete(msg);
        auditService.log(msg.getOrganization(), currentUserUtil.getCurrentUser(), "CONTACT_MESSAGE_DELETED",
            "CONTACT_MESSAGE", String.valueOf(id), "Deleted contact message from " + msg.getName(),
            null, null, "Messages");
        return ResponseEntity.noContent().build();
    }
}
