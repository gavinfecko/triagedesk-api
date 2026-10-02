package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.common.security.JwtConfig;
import dev.gavinfecko.triagedesk.common.security.JwtProperties;
import dev.gavinfecko.triagedesk.identity.domain.RefreshToken;
import dev.gavinfecko.triagedesk.identity.domain.UserAccount;
import dev.gavinfecko.triagedesk.identity.infra.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Mints the access/refresh pair for a login session ("family"). */
@Component
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtConfig.Signing signing;
    private final JwtProperties properties;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public TokenService(
            JwtConfig.Signing signing, JwtProperties properties, RefreshTokenRepository refreshTokens, Clock clock) {
        this.signing = signing;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /** What the auth endpoints return. */
    public record TokenPair(String accessToken, String refreshToken, String tokenType, long expiresIn) {}

    /** Issues a new pair in {@code family}; the stored refresh token is returned so a caller can link it. */
    public Issued issue(UserAccount user, UUID family) {
        Instant now = clock.instant();
        String refreshValue = randomToken();
        RefreshToken stored = refreshTokens.save(
                new RefreshToken(user.id(), family, hash(refreshValue), now, now.plus(properties.refreshTokenTtl())));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .claim(CurrentUser.ROLE_CLAIM, user.role().name())
                .claim(CurrentUser.FAMILY_CLAIM, family.toString())
                .build();
        String access = signing.encoder()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(signing.algorithm()).build(), claims))
                .getTokenValue();
        return new Issued(
                new TokenPair(
                        access,
                        refreshValue,
                        "Bearer",
                        properties.accessTokenTtl().toSeconds()),
                stored);
    }

    public record Issued(TokenPair pair, RefreshToken stored) {}

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of a refresh token value; the only form ever stored. */
    public static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
