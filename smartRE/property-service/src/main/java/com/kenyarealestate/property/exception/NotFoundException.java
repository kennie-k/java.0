package com.kenyarealestate.property.exception;

/** The requested resource does not exist (or is hidden from this caller) — maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
