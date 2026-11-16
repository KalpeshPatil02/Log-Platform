package com.logplatform.ingestion.exception;

import com.logplatform.common.ApiException;

/** 503 raised when the raw stream reaches its configured high-water mark. */
public final class BackpressureException extends ApiException {
    public BackpressureException() { super(503, "BACKPRESSURE", "Ingestion pipeline is saturated, retry later"); }
}
