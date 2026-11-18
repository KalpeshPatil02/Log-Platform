package com.logplatform.notification.web;

import java.util.regex.Pattern;

/** Validates the X-Tenant-Id header injected by the gateway. Tenant ids are UUID strings (identity-service). */
final class TenantHeader {
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{1,36}$");

    private TenantHeader() {}

    static String require(String header) {
        if (header == null || !VALID.matcher(header.trim()).matches()) {
            throw new IllegalArgumentException("Missing or invalid trusted X-Tenant-Id");
        }
        return header.trim();
    }
}
