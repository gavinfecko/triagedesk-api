package dev.gavinfecko.triagedesk;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A PostgreSQL 16 container per test context, wired in as the datasource. Never H2 (ADR-0004).
 * The wait strategy also checks the mapped port from the host: on Colima the forwarded port can
 * lag the container's own "ready" log line by a moment.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2))
                        .withStrategy(Wait.forListeningPort())
                        .withStartupTimeout(Duration.ofSeconds(90)));
    }
}
