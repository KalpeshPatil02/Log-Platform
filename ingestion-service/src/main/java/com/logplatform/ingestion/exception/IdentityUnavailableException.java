package com.logplatform.ingestion.exception;

import com.logplatform.common.ApiException;

/** Fail closed when identity-service cannot validate an API key. */
public class IdentityUnavailableException extends ApiException {
    public IdentityUnavailableException(String message, Throwable cause) {
        super(503, "IDENTITY_UNAVAILABLE", message);
        if (cause != null) initCause(cause);
    }
}
