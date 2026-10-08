package dev.gavinfecko.triagedesk.common.time;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs run unless {@code triagedesk.scheduling.enabled=false} (tests call the jobs directly). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "triagedesk.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {}
