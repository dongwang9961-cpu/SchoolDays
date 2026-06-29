package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class NotificationController extends ApiPlaceholderSupport {

    @GetMapping("/notification-providers")
    public ResponseEntity<EndpointStatusResponse> listNotificationProviders(@PathVariable("tenantId") UUID tenantId) {
        return notImplemented("GET /api/tenants/{tenantId}/notification-providers");
    }

    @PostMapping("/notification-providers")
    public ResponseEntity<EndpointStatusResponse> createNotificationProvider(
            @PathVariable("tenantId") UUID tenantId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/notification-providers");
    }

    @PatchMapping("/notification-providers/{providerId}")
    public ResponseEntity<EndpointStatusResponse> updateNotificationProvider(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("providerId") UUID providerId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("PATCH /api/tenants/{tenantId}/notification-providers/{providerId}");
    }

    @PostMapping("/notifications")
    public ResponseEntity<EndpointStatusResponse> sendNotification(
            @PathVariable("tenantId") UUID tenantId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/notifications");
    }

    @GetMapping("/notification-history")
    public ResponseEntity<EndpointStatusResponse> listNotificationHistory(@PathVariable("tenantId") UUID tenantId) {
        return notImplemented("GET /api/tenants/{tenantId}/notification-history");
    }
}
