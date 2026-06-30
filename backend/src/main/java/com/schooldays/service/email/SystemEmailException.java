package com.schooldays.service.email;

public class SystemEmailException extends RuntimeException {

    public SystemEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
