package com.schooldays.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.schooldays.service.auth.InvalidAuthRequestException;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> authenticationFailed() {
        return Map.of("error", "Invalid email or password");
    }

    @ExceptionHandler(InvalidAuthRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidAuthRequest(InvalidAuthRequestException exception) {
        return Map.of("error", exception.getMessage());
    }
}
