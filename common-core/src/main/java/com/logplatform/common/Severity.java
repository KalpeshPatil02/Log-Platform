package com.logplatform.common;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalized log severity. {@link #parse(String)} maps common aliases from different logging frameworks.
 */
public enum Severity {
    TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

    public boolean atLeast(Severity other) {
        return this.ordinal() >= other.ordinal();
    }

    public static Optional<Severity> parse(String raw) {
        if (raw == null) return Optional.empty();
        Severity s = switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE", "FINEST" -> TRACE;
            case "DEBUG", "FINE" -> DEBUG;
            case "INFO", "INFORMATION", "NOTICE" -> INFO;
            case "WARN", "WARNING" -> WARN;
            case "ERROR", "ERR", "SEVERE" -> ERROR;
            case "FATAL", "CRITICAL", "PANIC" -> FATAL;
            default -> null;
        };
        return Optional.ofNullable(s);
    }
}
