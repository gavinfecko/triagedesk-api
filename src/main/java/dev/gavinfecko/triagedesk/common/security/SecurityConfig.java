package dev.gavinfecko.triagedesk.common.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API security. Callers present a bearer access token (ADR-0006); its {@code role} claim
 * becomes the {@code ROLE_*} authority. Health probes and the auth endpoints are public, the other
 * Actuator endpoints are for admins, everything else needs a valid token. Security failures (401/403)
 * go through the MVC exception resolver so they are Problem Details like every other error. The
 * OpenAPI document and Swagger UI are public only where {@code triagedesk.docs.public} says so (dev).
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // `make seed` runs without a web server
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints an anonymous caller may POST to. */
    static final String[] PUBLIC_POSTS = {"/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh"};

    /** Working a ticket is for agents and admins; denied here before validation, and again in the service. */
    static final String[] STAFF_TICKET_ACTIONS = {"/api/v1/tickets/*/assign", "/api/v1/tickets/*/queue"};

    static final String[] DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            ObjectProvider<MeterRegistry> meters,
            @Value("${triagedesk.docs.public:false}") boolean docsPublic)
            throws Exception {
        AuthenticationEntryPoint entryPoint = (req, res, ex) -> unauthenticated(resolver, meters, req, res, ex);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter()))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((req, res, ex) -> resolver.resolveException(req, res, null, ex)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                            .permitAll();
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_POSTS).permitAll();
                    auth.requestMatchers(HttpMethod.POST, STAFF_TICKET_ACTIONS).hasAnyRole("AGENT", "ADMIN");
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated();
                    auth.requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN");
                    var docs = auth.requestMatchers(HttpMethod.GET, DOCS_PATHS);
                    if (docsPublic) {
                        docs.permitAll();
                    } else {
                        docs.hasRole("ADMIN");
                    }
                    auth.anyRequest().authenticated();
                })
                .build();
    }

    /** {@code sub} is the principal name; the single {@code role} claim becomes {@code ROLE_<role>}. */
    static JwtAuthenticationConverter roleClaimConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(CurrentUser.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    private static void unauthenticated(
            HandlerExceptionResolver resolver,
            ObjectProvider<MeterRegistry> meters,
            HttpServletRequest req,
            HttpServletResponse res,
            AuthenticationException ex) {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            meters.ifAvailable(m ->
                    m.counter("auth.login.failed", "reason", "invalid_token").increment());
        }
        res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolver.resolveException(req, res, null, ex);
    }
}
