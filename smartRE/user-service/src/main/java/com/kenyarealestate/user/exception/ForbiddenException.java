package com.kenyarealestate.user.exception;

/** Thrown when an authenticated caller lacks the privilege to perform an action. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
