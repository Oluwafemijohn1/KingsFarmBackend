package com.kingsfarm.kingsfarmbackend.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findAllByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(String recipient);

    List<AppNotification> findAllByRecipientAndReadAtIsNull(String recipient);

    boolean existsByRecipientAndTypeAndMessageAndReadAtIsNull(String recipient, String type, String message);
}
