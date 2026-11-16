package com.logplatform.identity.web;

import com.logplatform.identity.security.JwtKeyProvider;
import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** api-gateway (Phase 2) and other services fetch this to verify RS256 signatures. */
@RestController
public class JwksController {
    private final JwtKeyProvider keys;

    public JwksController(JwtKeyProvider keys) { this.keys = keys; }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return new JWKSet(keys.publicJwk()).toJSONObject();
    }
}
