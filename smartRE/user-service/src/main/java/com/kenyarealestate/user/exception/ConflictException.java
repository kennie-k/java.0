package com.kenyarealestate.user.exception;

/** Thrown when a request conflicts with existing state (duplicate email/phone, etc). Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
