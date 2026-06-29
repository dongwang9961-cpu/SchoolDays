package com.schooldays.dto.api;

public record EndpointStatusResponse(
        String status,
        String endpoint,
        String message
) {
}
