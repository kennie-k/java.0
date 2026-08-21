package com.kenyarealestate.viewing.exception;

/** The request conflicts with the current state of the resource — maps to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
