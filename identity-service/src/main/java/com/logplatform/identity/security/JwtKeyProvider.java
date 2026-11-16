package com.logplatform.identity.security;

import com.logplatform.identity.config.IdentityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Holds the RS256 key pair. Private key signs tokens; the public half is published at the JWKS endpoint. */
@Component
public class JwtKeyProvider {
    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);
    private final RSAKey rsaKey;

    public JwtKeyProvider(IdentityProperties props) {
        try {
            this.rsaKey = build(props.jwt());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialise JWT signing key", e);
        }
    }

    private RSAKey build(IdentityProperties.Jwt cfg) throws Exception {
        RSAPublicKey pub;
        RSAPrivateKey priv;
        if (StringUtils.hasText(cfg.privateKeyPath()) && StringUtils.hasText(cfg.publicKeyPath())) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(readPem(cfg.privateKeyPath())));
            pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(readPem(cfg.publicKeyPath())));
        } else {
            log.warn("No JWT key files configured - generating an EPHEMERAL RSA key. Tokens become invalid on restart.");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            pub = (RSAPublicKey) kp.getPublic();
            priv = (RSAPrivateKey) kp.getPrivate();
        }
        return new RSAKey.Builder(pub)
                .privateKey(priv)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint()
                .build();
    }

    private byte[] readPem(String path) throws IOException {
        String pem = Files.readString(Path.of(path))
                .replaceAll("-----(BEGIN|END) [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    public RSAKey signingKey() { return rsaKey; }

    /** Public half only - safe to publish. */
    public RSAKey publicJwk() { return rsaKey.toPublicJWK(); }
}
