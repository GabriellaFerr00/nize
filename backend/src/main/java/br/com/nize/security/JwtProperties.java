package br.com.nize.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "nize.jwt")
public record JwtProperties(String secret, Duration expiration) {

    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "NIZE_JWT_SECRET must be set to a secret of at least 32 bytes (nize.jwt.secret)");
        }
        if (expiration == null) {
            expiration = Duration.ofHours(1);
        }
    }
}
