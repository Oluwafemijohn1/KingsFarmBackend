package com.kingsfarm.kingsfarmbackend.notification;

import com.kingsfarm.kingsfarmbackend.user.Role;
import com.kingsfarm.kingsfarmbackend.user.User;
import com.kingsfarm.kingsfarmbackend.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * In-app notifications (the bell). Each one goes to specific people - the person a request is for, the
 * administrators, or the managers of one module - so nobody sees alerts that belong to another module.
 */
@Service
public class AppNotificationService {

    private final AppNotificationRepository repository;
    private final UserRepository userRepository;

    public AppNotificationService(AppNotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /** One notification for one person. Blank recipients are ignored. */
    @Transactional
    public void notifyUser(String username, String type, String title, String message, String link) {
        if (username == null || username.isBlank()) {
            return;
        }
        repository.save(AppNotification.builder()
                .recipient(username).type(type).title(trim(title, 160)).message(trim(message, 500)).link(link).build());
    }

    /** One notification for every active person holding the role. */
    @Transactional
    public void notifyRole(Role role, String type, String title, String message, String link) {
        for (User u : userRepository.findAllByRoleAndActiveTrue(role)) {
            notifyUser(u.getUsername(), type, title, message, link);
        }
    }

    /**
     * Like {@link #notifyRole} but skips anyone who already has the same alert still unread, so a standing
     * condition (such as an ingredient staying below its minimum) is not repeated on every action.
     */
    @Transactional
    public void notifyRoleOnce(Role role, String type, String title, String message, String link) {
        for (User u : userRepository.findAllByRoleAndActiveTrue(role)) {
            if (!repository.existsByRecipientAndTypeAndMessageAndReadAtIsNull(u.getUsername(), type, trim(message, 500))) {
                notifyUser(u.getUsername(), type, title, message, link);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AppNotification> latest(String username, int limit) {
        return repository.findAllByRecipientOrderByCreatedAtDesc(username, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return repository.countByRecipientAndReadAtIsNull(username);
    }

    @Transactional
    public void markRead(Long id, String username) {
        repository.findById(id).ifPresent(n -> {
            if (n.getRecipient().equals(username) && n.getReadAt() == null) {
                n.setReadAt(Instant.now());
                repository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(String username) {
        Instant now = Instant.now();
        List<AppNotification> unread = repository.findAllByRecipientAndReadAtIsNull(username);
        unread.forEach(n -> n.setReadAt(now));
        repository.saveAll(unread);
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
