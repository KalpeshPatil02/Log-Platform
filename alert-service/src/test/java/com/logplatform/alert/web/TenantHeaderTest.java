package com.logplatform.alert.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantHeaderTest {

    @Test
    void acceptsUuidAndNumericTenantIds() {
        assertEquals("4f1c9d7e-1111-2222-3333-444455556666",
                TenantHeader.require("4f1c9d7e-1111-2222-3333-444455556666"));
        assertEquals("42", TenantHeader.require(" 42 "));
    }

    @Test
    void rejectsMissingBlankOrSuspiciousValues() {
        assertThrows(IllegalArgumentException.class, () -> TenantHeader.require(null));
        assertThrows(IllegalArgumentException.class, () -> TenantHeader.require(""));
        assertThrows(IllegalArgumentException.class, () -> TenantHeader.require("a b"));
        assertThrows(IllegalArgumentException.class, () -> TenantHeader.require("x'; DROP TABLE incidents;--"));
        assertThrows(IllegalArgumentException.class, () -> TenantHeader.require("a".repeat(37)));
    }
}
