package com.kenyarealestate.viewing.exception;

/** The caller's identity could not be established — maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
