package com.patrick.fintech.loan_backend.controller;

import com.patrick.fintech.loan_backend.dto.ApiResponse;
import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.NotificationRepository;
import com.patrick.fintech.loan_backend.util.CurrentUserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepo;
    private final CurrentUserUtil currentUserUtil;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getMine() {
        User user = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(notificationRepo.findTop20ByUserOrderByCreatedAtDesc(user)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        User user = currentUserUtil.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationRepo.countByUserAndReadFalse(user))));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable Long id) {
        User user = currentUserUtil.getCurrentUser();
        Notification n = notificationRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        if (n.getUser() == null || !n.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Access denied");
        n.setRead(true);
        n.setReadAt(LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.ok(notificationRepo.save(n)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<String>> markAllRead() {
        User user = currentUserUtil.getCurrentUser();
        List<Notification> unread = notificationRepo.findByUserAndReadFalseOrderByCreatedAtDesc(user);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> { n.setRead(true); n.setReadAt(now); });
        notificationRepo.saveAll(unread);
        return ResponseEntity.ok(ApiResponse.ok(unread.size() + " notification(s) marked as read"));
    }
}
