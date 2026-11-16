package com.logplatform.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.logplatform.ingestion.security.ApiKeyPrincipal;
import com.logplatform.ingestion.security.ApiKeyResolver;
import com.logplatform.ingestion.service.LogProducer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Docker NOT needed: the producer is mocked to behave like a dead Redis. */
@SpringBootTest
@AutoConfigureMockMvc
class RedisDownTest {

    @MockBean ApiKeyResolver resolver;
    @MockBean LogProducer producer;
    @Autowired MockMvc mvc;

    @Test
    void redisFailureIs503WithRetryAfterAndCorrelationIdNotASilentDrop() throws Exception {
        when(resolver.resolve("lap_good_key")).thenReturn(Optional.of(new ApiKeyPrincipal("tenant-1", "key-1")));
        when(producer.publish(any(), any(), anyList())).thenThrow(new RedisConnectionFailureException("redis down"));

        mvc.perform(post("/api/v1/logs")
                        .header("X-API-Key", "lap_good_key")
                        .header("X-Correlation-Id", "trace-12345678")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"service\":\"a\",\"environment\":\"prod\",\"severity\":\"INFO\",\"message\":\"m\"}"))
           .andExpect(status().isServiceUnavailable())
           .andExpect(header().string("Retry-After", "5"))
           .andExpect(header().string("X-Correlation-Id", "trace-12345678"))
           .andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"))
           .andExpect(jsonPath("$.correlationId").value("trace-12345678"));
    }
}
