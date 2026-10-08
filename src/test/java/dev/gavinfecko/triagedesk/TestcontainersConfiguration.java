package dev.gavinfecko.triagedesk;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A PostgreSQL 16 container per test context, wired in as the datasource (never H2, ADR-0004), and Mailpit as the
 * SMTP server.
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

    /** Mailpit catches every email the tests send; its HTTP API lets a test read them back (TD-50). */
    @Bean
    GenericContainer<?> mailpitContainer() {
        return new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
                .withExposedPorts(SMTP_PORT, API_PORT)
                .waitingFor(Wait.forHttp("/readyz").forPort(API_PORT).withStartupTimeout(Duration.ofSeconds(60)));
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(GenericContainer<?> mailpitContainer) {
        return registry -> {
            registry.add("spring.mail.host", mailpitContainer::getHost);
            registry.add("spring.mail.port", () -> mailpitContainer.getMappedPort(SMTP_PORT));
            registry.add(
                    "test.mailpit.api",
                    () -> "http://" + mailpitContainer.getHost() + ":" + mailpitContainer.getMappedPort(API_PORT));
        };
    }

    static final int SMTP_PORT = 1025;
    static final int API_PORT = 8025;
}
