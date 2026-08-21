package com.kenyarealestate.user.exception;

/** Thrown when a caller is being rate limited / locked out. Mapped to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
