package com.kenyarealestate.property.exception;

/** The caller is authenticated but not allowed to perform this action — maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
