package com.patrick.fintech.loan_backend.service;

import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;
import com.patrick.fintech.loan_backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * In-app notifications only (the dashboard bell / notifications page) — email lives in
 * MailService and SMS lives in SmsService. This used to also contain every email template,
 * which meant one class was doing two unrelated jobs; splitting it out mirrors how SMS was
 * already separate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;

    /** Creates one in-app notification per user. Safe to call with an empty list. */
    public void notifyUsers(List<User> users, String title, String message, String type, String link) {
        for (User u : users) {
            try {
                notificationRepo.save(Notification.builder()
                    .user(u).institution(u.getOrganization())
                    .title(title).message(message).type(type).link(link)
                    .build());
            } catch (Exception e) {
                log.warn("In-app notification failed for user {}: {}", u.getId(), e.getMessage());
            }
        }
    }
}
