package com.kenyarealestate.user.exception;

/** Thrown for bad credentials / failed authentication. Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
