package dev.gavinfecko.triagedesk.common.security;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token settings. Exactly one signing key is used: {@code private-key} (RS256, PKCS#8 PEM) when set,
 * otherwise {@code secret} (HS256, 32+ bytes). Production requires the private key.
 */
@ConfigurationProperties("triagedesk.jwt")
public record JwtProperties(
        String issuer,
        @Nullable String secret,
        @Nullable String privateKey,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {}
