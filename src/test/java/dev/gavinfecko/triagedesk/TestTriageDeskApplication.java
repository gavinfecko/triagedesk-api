package dev.gavinfecko.triagedesk;

import org.springframework.boot.SpringApplication;

/** Runs the app against a throwaway Testcontainers database: {@code ./mvnw spring-boot:test-run}. */
public class TestTriageDeskApplication {

    public static void main(String[] args) {
        SpringApplication.from(TriageDeskApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
