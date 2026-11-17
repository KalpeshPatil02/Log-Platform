package com.logplatform.alert.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IncidentEntityTest {

    private IncidentEntity incident() {
        return new IncidentEntity("4f1c9d7e-1111-2222-3333-444455556666", 1L, "fp", "title", "desc", Instant.now());
    }

    @Test
    void activeIncidentHoldsItsFingerprintAsActiveKey() {
        assertEquals("fp", incident().getActiveFingerprint());
    }

    @Test
    void acknowledgedIncidentIsStillActive() {
        IncidentEntity i = incident();
        i.acknowledge("kalpesh");
        assertEquals("fp", i.getActiveFingerprint());
    }

    @Test
    void resolvingClearsActiveKeySoTheSameFingerprintCanBeResolvedAgain() {
        IncidentEntity first = incident();
        first.resolve(Instant.now());
        IncidentEntity second = incident();
        second.resolve(Instant.now());

        assertNull(first.getActiveFingerprint());
        assertNull(second.getActiveFingerprint());
        assertEquals("RESOLVED", second.getStatus());
    }

    @Test
    void tenantIdKeepsUuidString() {
        assertEquals("4f1c9d7e-1111-2222-3333-444455556666", incident().getTenantId());
    }
}
