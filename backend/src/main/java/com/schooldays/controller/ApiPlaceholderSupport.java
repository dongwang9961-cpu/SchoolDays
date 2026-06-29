package com.schooldays.controller;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

abstract class ApiPlaceholderSupport {

    protected ResponseEntity<EndpointStatusResponse> notImplemented(String endpoint) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new EndpointStatusResponse(
                        "not_implemented",
                        endpoint,
                        "The endpoint route is available, but the domain service has not been implemented yet."
                ));
    }
}
