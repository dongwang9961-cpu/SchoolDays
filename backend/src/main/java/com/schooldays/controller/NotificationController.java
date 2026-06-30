package com.schooldays.controller;

import java.util.UUID;

import com.schooldays.dto.notification.GmailConnectStartRequest;
import com.schooldays.dto.notification.GmailConnectStartResponse;
import com.schooldays.dto.notification.NotificationHistoryListResponse;
import com.schooldays.dto.notification.NotificationHistoryResponse;
import com.schooldays.dto.notification.NotificationProviderListResponse;
import com.schooldays.dto.notification.SendNotificationRequest;
import com.schooldays.service.notification.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notification-providers")
    public NotificationProviderListResponse listNotificationProviders(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return notificationService.listProviders(tenantId, userId(authentication));
    }

    @PostMapping("/notification-providers/gmail/start")
    public GmailConnectStartResponse startGmailConnection(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication,
            @RequestBody(required = false) GmailConnectStartRequest request
    ) {
        return notificationService.startGmailConnection(tenantId, userId(authentication), request);
    }

    @PostMapping("/notifications")
    public NotificationHistoryResponse sendNotification(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication,
            @Valid @RequestBody SendNotificationRequest request
    ) {
        return notificationService.sendNotification(tenantId, userId(authentication), request);
    }

    @GetMapping("/notification-history")
    public NotificationHistoryListResponse listNotificationHistory(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return notificationService.listHistory(tenantId, userId(authentication));
    }

    private UUID userId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return UUID.fromString(jwt.getSubject());
    }
}
