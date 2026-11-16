package com.logplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.logplatform.identity.security.InternalTokenFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Needs Docker running. Exercises the whole Phase 1 story against a real MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class IdentityFlowIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @Test
    void fullTenantUserApiKeyLifecycle() throws Exception {
        // 1. bootstrap platform admin logs in
        String platformToken = login("platform", "admin@platform.local", "Root123");

        // 2. platform admin creates a tenant (+ its first tenant admin)
        mvc.perform(json(post("/api/v1/tenants"), platformToken, """
                {"name":"Acme","slug":"acme","adminEmail":"boss@acme.io",
                 "adminPassword":"S3cretPassw0rd","adminFullName":"Acme Boss"}"""))
           .andExpect(status().isCreated());

        // 3. tenant admin logs in; cannot create tenants (403)
        String tenantToken = login("acme", "boss@acme.io", "S3cretPassw0rd");
        mvc.perform(json(post("/api/v1/tenants"), tenantToken, """
                {"name":"Evil","slug":"evil","adminEmail":"x@evil.io",
                 "adminPassword":"S3cretPassw0rd","adminFullName":"X"}"""))
           .andExpect(status().isForbidden());

        // 4. tenant admin creates an API key
        String body = mvc.perform(json(post("/api/v1/api-keys"), tenantToken, "{\"name\":\"billing-service\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode created = om.readTree(body);
        String apiKey = created.get("apiKey").asText();
        String keyId = created.get("id").asText();

        // 5. internal validation works with the shared secret, not without it
        mvc.perform(post("/internal/api-keys/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"" + apiKey + "\"}"))
           .andExpect(status().isUnauthorized());
        JsonNode ok = validate(apiKey);
        assertThat(ok.get("valid").asBoolean()).isTrue();

        // 6. revoke -> validation now fails
        mvc.perform(delete("/api/v1/api-keys/" + keyId).header("Authorization", "Bearer " + tenantToken))
           .andExpect(status().isNoContent());
        JsonNode revoked = validate(apiKey);
        assertThat(revoked.get("valid").asBoolean()).isFalse();
        assertThat(revoked.get("reason").asText()).isEqualTo("REVOKED");

        // 7. wrong password -> 401
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantSlug\":\"acme\",\"email\":\"boss@acme.io\",\"password\":\"wrong-password\"}"))
           .andExpect(status().isUnauthorized());
    }

    private String login(String slug, String email, String password) throws Exception {
        String res = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(java.util.Map.of("tenantSlug", slug, "email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asText();
    }

    private JsonNode validate(String apiKey) throws Exception {
        String res = mvc.perform(post("/internal/api-keys/validate")
                .header(InternalTokenFilter.HEADER, "dev-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"" + apiKey + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String content) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content);
    }
}
