package com.schooldays.controller;

import com.schooldays.dto.api.EndpointStatusResponse;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

abstract class ApiPlaceholderSupport {

    protected ResponseEntity<EndpointStatusResponse> notImplemented(String endpoint) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new EndpointStatusResponse(
                        "not_implemented",
                        endpoint,
                        "The endpoint route is available, but the domain service has not been implemented yet."
                ));
    }

    protected UUID userId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return UUID.fromString(jwt.getSubject());
    }
}
