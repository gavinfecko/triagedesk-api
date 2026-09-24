package dev.gavinfecko.triagedesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Migrations apply from an empty PostgreSQL on every test run; this pins what the baseline must leave behind. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationsTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayAppliedEveryVersionInOrder() {
        List<String> versions = jdbc.sql(
                        "select version from flyway_schema_history where success order by installed_rank")
                .query(String.class)
                .list();
        assertThat(versions).startsWith("1").doesNotHaveDuplicates();
    }

    @Test
    void baselineEnablesTheExtensionsTheSchemaDependsOn() {
        List<String> extensions =
                jdbc.sql("select extname from pg_extension").query(String.class).list();
        assertThat(extensions).contains("pg_trgm", "citext");
    }

    @Test
    void databaseTimeZoneIsUtc() {
        String zone = jdbc.sql("select current_setting('timezone')")
                .query(String.class)
                .single();
        assertThat(zone).isEqualTo("UTC");
    }
}
