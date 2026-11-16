package com.logplatform.common;

/**
 * Ordered by privilege: VIEWER (read) < DEVELOPER (manage rules/incidents) < ADMIN (users, keys, tenant settings).
 */
public enum Role {
    VIEWER, DEVELOPER, ADMIN;

    public boolean atLeast(Role required) {
        return this.ordinal() >= required.ordinal();
    }
}
