package com.logplatform.notification.web;

import java.util.Locale;
import java.util.Set;

/**
 * Role policy for notification-service.
 *
 *   read   (GET notifications) : PLATFORM_ADMIN, TENANT_ADMIN, DEVELOPER, VIEWER
 *   write  (retry failed/DLQ notifications) : TENANT_ADMIN, DEVELOPER
 *
 * No recognised role in the header means no access (fail closed).
 */
final class RoleGuard {
    static final Set<String> READ = Set.of("PLATFORM_ADMIN", "TENANT_ADMIN", "DEVELOPER", "VIEWER");
    static final Set<String> WRITE = Set.of("TENANT_ADMIN", "DEVELOPER");

    private RoleGuard() {}

    static void requireRead(String rolesHeader) {
        require(rolesHeader, READ);
    }

    static void requireWrite(String rolesHeader) {
        require(rolesHeader, WRITE);
    }

    private static void require(String rolesHeader, Set<String> allowed) {
        if (rolesHeader != null) {
            for (String role : rolesHeader.split(",")) {
                if (allowed.contains(role.trim().toUpperCase(Locale.ROOT))) {
                    return;
                }
            }
        }
        throw new ForbiddenException("Your role is not allowed to perform this action");
    }
}
