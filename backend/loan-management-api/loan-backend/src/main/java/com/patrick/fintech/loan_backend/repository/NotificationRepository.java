package com.patrick.fintech.loan_backend.repository;

import com.patrick.fintech.loan_backend.model.Notification;
import com.patrick.fintech.loan_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(User user);
    long countByUserAndReadFalse(User user);
    List<Notification> findTop20ByUserOrderByCreatedAtDesc(User user);
}
