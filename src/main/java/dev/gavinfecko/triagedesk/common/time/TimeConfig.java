package dev.gavinfecko.triagedesk.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The only place that reads the wall clock. Everything else injects {@link Clock} so tests can
 * control time; an ArchUnit rule stops direct {@code now()} calls elsewhere.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
