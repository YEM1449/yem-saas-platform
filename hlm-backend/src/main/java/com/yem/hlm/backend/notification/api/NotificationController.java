package com.yem.hlm.backend.notification.api;

import com.yem.hlm.backend.notification.api.dto.NotificationResponse;
import com.yem.hlm.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Notifications", description = "In-app CRM bell notifications")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(
            @RequestParam(value = "read", required = false) Boolean read,
            @RequestParam(value = "size", required = false, defaultValue = "50") int size
    ) {
        return notificationService.list(read, size);
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(id);
    }
}
