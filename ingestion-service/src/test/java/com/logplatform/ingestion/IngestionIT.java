package com.logplatform.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.logplatform.ingestion.support.CacheKeys;
import com.logplatform.common.Hashing;
import com.logplatform.ingestion.exception.IdentityUnavailableException;
import com.logplatform.ingestion.security.IdentityClient;
import com.logplatform.ingestion.security.IdentityClient.ValidationResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Needs Docker (real Redis). identity-service is replaced by a mock. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {"ingestion.max-stream-length=5", "platform.web.max-request-bytes=40000"})
class IngestionIT {
    static final String STREAM = "logs.raw";
    static final String GOOD = "lap_good_key";          // tenant-1
    static final String OTHER = "lap_other_key";        // tenant-2

    static final String EVENT = """
            {"service":"billing","environment":"prod","severity":"ERROR","message":"payment failed",
             "traceId":"t-1","tenantId":"evil-tenant","attributes":{"orderId":42}}""";

    static String event(String service, String extra) {
        return "{\"service\":\"" + service + "\",\"environment\":\"prod\",\"severity\":\"INFO\",\"message\":\"m\"" + extra + "}";
    }

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockBean IdentityClient identity;
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate template;

    @BeforeEach
    void clean() {
        Set<String> keys = template.keys("*");
        if (keys != null && !keys.isEmpty()) template.delete(keys);
        when(identity.validate(GOOD)).thenReturn(new ValidationResult(true, null, "tenant-1", "key-1"));
        when(identity.validate(OTHER)).thenReturn(new ValidationResult(true, null, "tenant-2", "key-2"));
        when(identity.validate("lap_revoked_key")).thenReturn(new ValidationResult(false, "REVOKED", null, null));
        when(identity.validate("lap_expired_key")).thenReturn(new ValidationResult(false, "EXPIRED", null, null));
        when(identity.validate("lap_identity_down")).thenThrow(new IdentityUnavailableException("down", null));
    }

    private MockHttpServletRequestBuilder req(String path, String key, String body) {
        MockHttpServletRequestBuilder b = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? b : b.header("X-API-Key", key);
    }

    private List<MapRecord<String, Object, Object>> stream() {
        return template.opsForStream().range(STREAM, Range.unbounded());
    }

    // ---------- success + contract ----------

    @Test
    void validEventIs202AndLandsInStreamWithTheExpectedFields() throws Exception {
        mvc.perform(req("/api/v1/logs", GOOD, EVENT))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(1))
           .andExpect(jsonPath("$.duplicates").value(0));

        assertThat(stream()).hasSize(1);
        var f = stream().get(0).getValue();
        assertThat(f.get("tenantId")).isEqualTo("tenant-1");
        assertThat(f.get("apiKeyId")).isEqualTo("key-1");
        assertThat(f.get("service")).isEqualTo("billing");
        assertThat(f.get("environment")).isEqualTo("prod");
        assertThat(f.get("severity")).isEqualTo("ERROR");
        assertThat(f.get("message")).isEqualTo("payment failed");
        assertThat(f.get("traceId")).isEqualTo("t-1");
        assertThat(f.get("eventId")).isNotNull();
        assertThat(f.get("receivedAt")).isNotNull();
        assertThat(f.get("attributes").toString()).contains("orderId");
    }

    @Test
    void batchIsAcceptedAsMultipleStreamEntries() throws Exception {
        mvc.perform(req("/api/v1/logs/batch", GOOD, "{\"events\":[" + event("a", "") + "," + event("b", "") + "]}"))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(2));

        assertThat(stream()).hasSize(2);
    }

    // ---------- API key validation ----------

    @Test
    void missingRevokedExpiredAndGarbageKeysAre401AndNothingIsWritten() throws Exception {
        mvc.perform(req("/api/v1/logs", null, EVENT)).andExpect(status().isUnauthorized());
        mvc.perform(req("/api/v1/logs", "lap_revoked_key", EVENT)).andExpect(status().isUnauthorized());
        mvc.perform(req("/api/v1/logs", "lap_expired_key", EVENT)).andExpect(status().isUnauthorized());
        mvc.perform(req("/api/v1/logs", "garbage", EVENT)).andExpect(status().isUnauthorized());

        assertThat(stream()).isEmpty();
    }

    @Test
    void identityOutageFailsClosedWith503() throws Exception {
        mvc.perform(req("/api/v1/logs", "lap_identity_down", EVENT))
           .andExpect(status().isServiceUnavailable())
           .andExpect(header().exists("Retry-After"));

        assertThat(stream()).isEmpty();
    }

    @Test
    void secondRequestWithSameKeyIsServedFromCache() throws Exception {
        mvc.perform(req("/api/v1/logs", GOOD, EVENT)).andExpect(status().isAccepted());
        mvc.perform(req("/api/v1/logs", GOOD, EVENT)).andExpect(status().isAccepted());

        verify(identity, times(1)).validate(GOOD);
    }

    @Test
    void evictingTheCacheEntryForcesRevalidationSoRevokeTakesEffectImmediately() throws Exception {
        mvc.perform(req("/api/v1/logs", GOOD, EVENT)).andExpect(status().isAccepted());
        template.delete(CacheKeys.apiKey(Hashing.sha256Hex(GOOD)));     // what identity does on revoke
        when(identity.validate(GOOD)).thenReturn(new ValidationResult(false, "REVOKED", null, null));

        mvc.perform(req("/api/v1/logs", GOOD, EVENT)).andExpect(status().isUnauthorized());
    }

    // ---------- tenant isolation ----------

    @Test
    void tenantIsAlwaysTakenFromTheKeyNeverFromThePayload() throws Exception {
        mvc.perform(req("/api/v1/logs", GOOD, event("svc-a", ",\"tenantId\":\"tenant-2\""))).andExpect(status().isAccepted());
        mvc.perform(req("/api/v1/logs", OTHER, event("svc-b", ",\"tenantId\":\"tenant-1\""))).andExpect(status().isAccepted());

        Map<String, String> tenantByService = new HashMap<>();
        stream().forEach(r -> tenantByService.put(r.getValue().get("service").toString(), r.getValue().get("tenantId").toString()));
        assertThat(tenantByService).containsEntry("svc-a", "tenant-1").containsEntry("svc-b", "tenant-2");
    }

    // ---------- payload validation ----------

    @Test
    void emptyBatchOversizedBatchMalformedJsonAndBadFieldsAre400AndNothingIsWritten() throws Exception {
        mvc.perform(req("/api/v1/logs/batch", GOOD, "{\"events\":[]}")).andExpect(status().isBadRequest());

        StringBuilder many = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 501; i++) many.append(i > 0 ? "," : "").append("{\"service\":\"a\",\"environment\":\"p\",\"severity\":\"I\",\"message\":\"m\"}");
        many.append("]}");
        mvc.perform(req("/api/v1/logs/batch", GOOD, many.toString())).andExpect(status().isBadRequest());

        mvc.perform(req("/api/v1/logs", GOOD, "{not json")).andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
        mvc.perform(req("/api/v1/logs", GOOD, event("a", ",\"eventId\":\"x\""))).andExpect(status().isBadRequest());   // eventId too short
        mvc.perform(req("/api/v1/logs", GOOD, "{\"service\":\"a\",\"environment\":\"prod\",\"severity\":\"INFO\",\"message\":\"\"}"))
           .andExpect(status().isBadRequest());

        assertThat(stream()).isEmpty();
    }

    @Test
    void batchWithOneBadEventIsRejectedAsAWhole() throws Exception {
        String bad = "{\"service\":\"a\",\"environment\":\"prod\",\"severity\":\"INFO\",\"message\":\"\"}";
        mvc.perform(req("/api/v1/logs/batch", GOOD, "{\"events\":[" + event("ok", "") + "," + bad + "]}"))
           .andExpect(status().isBadRequest());

        assertThat(stream()).isEmpty();
    }

    @Test
    void bodyOverTheConfiguredLimitIs413() throws Exception {
        String huge = event("a", "").replace("\"message\":\"m\"", "\"message\":\"" + "x".repeat(45000) + "\"");
        mvc.perform(req("/api/v1/logs", GOOD, huge))
           .andExpect(status().isPayloadTooLarge());

        assertThat(stream()).isEmpty();
    }

    // ---------- idempotency ----------

    @Test
    void sameEventIdTwiceIsQueuedOnceAndTheRetryIsToldItWasADuplicate() throws Exception {
        String body = event("billing", ",\"eventId\":\"evt-12345678\"");

        mvc.perform(req("/api/v1/logs", GOOD, body)).andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.duplicates").value(0));
        mvc.perform(req("/api/v1/logs", GOOD, body)).andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.duplicates").value(1))
           .andExpect(jsonPath("$.eventIds[0]").value("evt-12345678"));

        assertThat(stream()).hasSize(1);
    }

    @Test
    void dedupIsPerTenantSoTheSameEventIdFromAnotherTenantIsAccepted() throws Exception {
        String body = event("billing", ",\"eventId\":\"evt-12345678\"");

        mvc.perform(req("/api/v1/logs", GOOD, body)).andExpect(status().isAccepted());
        mvc.perform(req("/api/v1/logs", OTHER, body)).andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(1));

        assertThat(stream()).hasSize(2);
    }

    @Test
    void duplicateInsideOneBatchIsQueuedOnce() throws Exception {
        String e = event("billing", ",\"eventId\":\"evt-12345678\"");
        mvc.perform(req("/api/v1/logs/batch", GOOD, "{\"events\":[" + e + "," + e + "]}"))
           .andExpect(status().isAccepted())
           .andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.duplicates").value(1));

        assertThat(stream()).hasSize(1);
    }

    // ---------- correlation id / error shape ----------

    @Test
    void correlationIdIsEchoedAndAppearsInErrorBodies() throws Exception {
        mvc.perform(req("/api/v1/logs", GOOD, EVENT).header("X-Correlation-Id", "trace-12345678"))
           .andExpect(status().isAccepted())
           .andExpect(header().string("X-Correlation-Id", "trace-12345678"));

        mvc.perform(req("/api/v1/logs", "garbage", EVENT).header("X-Correlation-Id", "trace-12345678"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
           .andExpect(jsonPath("$.correlationId").value("trace-12345678"));

        assertThat(stream().get(0).getValue().get("correlationId")).isEqualTo("trace-12345678");
    }

    // ---------- backpressure ----------

    @Test
    void fullStreamTriggersBackpressure503() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(req("/api/v1/logs", GOOD, EVENT)).andExpect(status().isAccepted());
        }
        mvc.perform(req("/api/v1/logs", GOOD, EVENT))
           .andExpect(status().isServiceUnavailable())
           .andExpect(header().string("Retry-After", "5"))
           .andExpect(jsonPath("$.code").value("BACKPRESSURE"));

        assertThat(stream()).hasSize(5);
    }
}
