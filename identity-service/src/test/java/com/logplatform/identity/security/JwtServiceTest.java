package com.logplatform.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.logplatform.identity.config.IdentityProperties;
import com.logplatform.identity.domain.Role;
import com.logplatform.identity.domain.User;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void tokenSignedWithPrivateKeyVerifiesWithPublishedPublicJwkOnly() throws Exception {
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Jwt("http://localhost:8081", Duration.ofMinutes(15), null, null),
                "t", new IdentityProperties.Bootstrap(null, null));
        JwtKeyProvider keys = new JwtKeyProvider(props);
        JwtService service = new JwtService(keys, props);

        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("dev@acme.io");
        user.setRoles(Set.of(Role.DEVELOPER));

        String token = service.issue(user).accessToken();

        RSAKey publicJwk = keys.publicJwk();
        assertThat(publicJwk.isPrivate()).isFalse();

        Jwt jwt = NimbusJwtDecoder.withPublicKey(publicJwk.toRSAPublicKey()).build().decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId());
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("tenant-1");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("DEVELOPER");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo(publicJwk.getKeyID());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Jwt("http://localhost:8081", Duration.ofMinutes(15), null, null),
                "t", new IdentityProperties.Bootstrap(null, null));

        JwtKeyProvider keys = new JwtKeyProvider(props);
        JwtService service = new JwtService(keys, props);

        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("dev@acme.io");
        user.setRoles(Set.of(Role.DEVELOPER));

        String token = service.issue(user).accessToken();
        String tamperedToken = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(keys.publicJwk().toRSAPublicKey()).build();

        assertThatThrownBy(() -> decoder.decode(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentPrivateKeyIsRejected() throws Exception {
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Jwt("http://localhost:8081", Duration.ofMinutes(15), null, null),
                "t", new IdentityProperties.Bootstrap(null, null));

        JwtKeyProvider signingKeys = new JwtKeyProvider(props);
        JwtKeyProvider differentKeys = new JwtKeyProvider(props);

        JwtService service = new JwtService(signingKeys, props);

        User user = new User();
        user.setTenantId("tenant-1");
        user.setEmail("dev@acme.io");
        user.setRoles(Set.of(Role.DEVELOPER));

        String token = service.issue(user).accessToken();

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(differentKeys.publicJwk().toRSAPublicKey()).build();

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        IdentityProperties props = new IdentityProperties(
                new IdentityProperties.Jwt("http://localhost:8081",
                        Duration.ofMinutes(15), null, null),
                "t", new IdentityProperties.Bootstrap(null, null));

        JwtKeyProvider keys = new JwtKeyProvider(props);

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("user-1")
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .id(UUID.randomUUID().toString())
                .claim("tenant_id", "tenant-1")
                .claim("email", "dev@acme.io")
                .claim("roles", List.of("DEVELOPER"))
                .build();

        NimbusJwtEncoder encoder =
                new NimbusJwtEncoder(
                        new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));

        String token = encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId(keys.signingKey().getKeyID())
                                .build(),
                        claims)).getTokenValue();

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(
                        keys.publicJwk().toRSAPublicKey()).build();

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }
}
