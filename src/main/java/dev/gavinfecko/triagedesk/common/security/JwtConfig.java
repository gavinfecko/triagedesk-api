package dev.gavinfecko.triagedesk.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Builds the token encoder and decoder from {@link JwtProperties}. The API is its own token issuer
 * and resource server (ADR-0006); pointing the decoder at an external issuer later is a config change.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** The signing setup the rest of the app needs: an encoder and the algorithm to put in the header. */
    public record Signing(JwtEncoder encoder, JwsAlgorithm algorithm) {}

    @Bean
    Keys jwtKeys(JwtProperties properties) {
        return Keys.from(properties);
    }

    @Bean
    Signing jwtSigning(Keys keys) {
        return keys.signing();
    }

    @Bean
    JwtDecoder jwtDecoder(Keys keys, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = keys.decoder();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(new JwtIssuerValidator(properties.issuer()), timestamps));
        return decoder;
    }

    /** Either an RSA key pair or a shared HMAC secret. */
    public sealed interface Keys {

        Signing signing();

        NimbusJwtDecoder decoder();

        static Keys from(JwtProperties properties) {
            if (hasText(properties.privateKey())) {
                return Rsa.fromPem(properties.privateKey());
            }
            if (hasText(properties.secret())) {
                return Hmac.fromSecret(properties.secret());
            }
            throw new IllegalStateException(
                    "No JWT signing key: set triagedesk.jwt.private-key (RS256) or triagedesk.jwt.secret (HS256)");
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    record Hmac(SecretKey key) implements Keys {

        static Hmac fromSecret(String secret) {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("triagedesk.jwt.secret must be at least 32 bytes for HS256");
            }
            return new Hmac(new SecretKeySpec(bytes, "HmacSHA256"));
        }

        @Override
        public Signing signing() {
            return new Signing(new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key)), MacAlgorithm.HS256);
        }

        @Override
        public NimbusJwtDecoder decoder() {
            return NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }
    }

    record Rsa(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) implements Keys {

        static Rsa fromPem(String pem) {
            String base64 =
                    pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                RSAPrivateCrtKey priv = (RSAPrivateCrtKey) factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
                RSAPublicKey pub = (RSAPublicKey)
                        factory.generatePublic(new RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent()));
                return new Rsa(pub, priv);
            } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
                throw new IllegalStateException("triagedesk.jwt.private-key is not a PKCS#8 RSA private key", e);
            }
        }

        @Override
        public Signing signing() {
            RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
            return new Signing(
                    new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk))),
                    SignatureAlgorithm.RS256);
        }

        @Override
        public NimbusJwtDecoder decoder() {
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        }
    }
}
