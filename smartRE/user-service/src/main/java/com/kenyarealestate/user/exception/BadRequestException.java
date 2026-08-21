package com.kenyarealestate.user.exception;

/** Thrown for generic invalid-input / invalid-state errors that aren't covered by a more specific type. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
