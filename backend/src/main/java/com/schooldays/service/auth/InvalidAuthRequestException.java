package com.schooldays.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAuthRequestException extends RuntimeException {

    public InvalidAuthRequestException(String message) {
        super(message);
    }
}
