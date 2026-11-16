package com.logplatform.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CommonCoreTest {
    @Test
    void severityAliasesAreNormalized() {
        assertEquals(Severity.WARN, Severity.parse("warning").orElseThrow());
        assertEquals(Severity.ERROR, Severity.parse(" SEVERE ").orElseThrow());
        assertEquals(Severity.FATAL, Severity.parse("critical").orElseThrow());
        assertTrue(Severity.parse("banana").isEmpty());
        assertTrue(Severity.parse(null).isEmpty());
    }

    @Test
    void severityOrdering() {
        assertTrue(Severity.ERROR.atLeast(Severity.WARN));
        assertFalse(Severity.INFO.atLeast(Severity.ERROR));
    }

    @Test
    void roleOrdering() {
        assertTrue(Role.ADMIN.atLeast(Role.DEVELOPER));
        assertFalse(Role.VIEWER.atLeast(Role.DEVELOPER));
    }

    @Test
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Hashing.sha256Hex("abc"));
    }

    @Test
    void apiExceptionCarriesStatusAndCode() {
        ApiException e = ApiException.conflict("DUPLICATE", "exists");
        assertEquals(409, e.getStatus());
        assertEquals("DUPLICATE", e.getCode());
    }
}
