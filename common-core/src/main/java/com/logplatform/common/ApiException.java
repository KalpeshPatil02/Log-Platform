package com.logplatform.common;

/**
 * Business exception carrying an HTTP status and a stable machine-readable code. Mapped to JSON by common-web.
 */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException badRequest(String code, String msg) {
        return new ApiException(400, code, msg);
    }

    public static ApiException unauthorized(String msg) {
        return new ApiException(401, "UNAUTHORIZED", msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, "FORBIDDEN", msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(404, "NOT_FOUND", msg);
    }

    public static ApiException conflict(String code, String msg) {
        return new ApiException(409, code, msg);
    }

    public static ApiException tooManyRequests(String msg) {
        return new ApiException(429, "RATE_LIMITED", msg);
    }

    public static ApiException unavailable(String msg) {
        return new ApiException(503, "UNAVAILABLE", msg);
    }
}
