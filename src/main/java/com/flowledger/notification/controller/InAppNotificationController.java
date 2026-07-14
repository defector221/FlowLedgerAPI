package com.flowledger.notification.controller;

import com.flowledger.notification.dto.InAppNotificationDtos.InAppNotificationResponse;
import com.flowledger.notification.dto.InAppNotificationDtos.UnreadCountResponse;
import com.flowledger.notification.service.InAppNotificationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class InAppNotificationController {
    private final InAppNotificationService service;

    public InAppNotificationController(InAppNotificationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<InAppNotificationResponse> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/{id}/read")
    public InAppNotificationResponse markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    public UnreadCountResponse markAllRead() {
        return service.markAllRead();
    }
}
