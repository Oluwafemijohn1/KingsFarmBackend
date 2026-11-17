package com.kingsfarm.kingsfarmbackend.notification;

import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** The signed-in person's own notifications (the bell). Anyone signed in may read and clear their own. */
@RestController
@RequestMapping("/api/v1/notifications")
public class AppNotificationController {

    public record Item(Long id, String type, String title, String message, String link, Instant createdAt, boolean read) {
    }

    public record Inbox(long unread, List<Item> items) {
    }

    private final AppNotificationService service;

    public AppNotificationController(AppNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Inbox inbox(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        List<Item> items = service.latest(principal.username(), 30).stream()
                .map(n -> new Item(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
        return new Inbox(service.unreadCount(principal.username()), items);
    }

    @PostMapping("/{id}/read")
    public void read(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long id) {
        service.markRead(id, principal.username());
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        service.markAllRead(principal.username());
    }
}
