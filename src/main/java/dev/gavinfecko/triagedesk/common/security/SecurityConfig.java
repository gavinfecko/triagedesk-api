package dev.gavinfecko.triagedesk.common.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless API security. Health probes are public so the platform can check liveness and
 * readiness; everything else requires authentication. Security failures (401/403) are routed to
 * the MVC exception resolver so they come out as Problem Details like every other error. The
 * OpenAPI document and Swagger UI are public only where {@code triagedesk.docs.public} says so
 * (dev); otherwise they need the ADMIN role. JWT support arrives with TD-11.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String[] DOCS_PATHS = {
        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**"
    };

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            @Value("${triagedesk.docs.public:false}") boolean docsPublic)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> resolver.resolveException(req, res, null, ex))
                        .accessDeniedHandler((req, res, ex) -> resolver.resolveException(req, res, null, ex)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll();
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
}
