package com.logplatform.alert.web;

/** Thrown when the caller's role may not perform the request. Mapped to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
