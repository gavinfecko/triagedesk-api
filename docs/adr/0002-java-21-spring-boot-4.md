# ADR-0002: Java 21 LTS and Spring Boot 4.1

**Status:** Accepted · **Date:** 2026-09-24 · **Confirm in Sprint 0:** TD-9 fills in exact versions and starter names.

## Context
The project's purpose includes proving Java and Spring skills that hiring teams (federal contractors in particular) will recognise. Those teams run LTS Java and the current or previous Spring Boot line. Spring Initializr's default at project start (2026-09-24) is Spring Boot 4.1.1 with Java 17 as default and 21/25 offered. Boot 4 (Spring Framework 7, Spring Security 7) reorganised starter artifacts and changed some defaults, so most tutorials online still describe Boot 3.

## Decision
- **Java 21** (LTS). Not 17 (older than what new projects start on), not 25 (also LTS, but library and tooling support was thinner at the time; upgrade is a one-line change later).
- **Spring Boot 4.1.x**, the current GA. Dependencies and starter names come from Spring Initializr, never copied from Boot 3 material. Virtual threads on (`spring.threads.virtual.enabled=true`).
- Versions pinned in `pom.xml` properties; Dependabot proposes upgrades weekly.

## Consequences
- Modern language features (records, sealed interfaces, pattern matching for switch) are used in the domain model.
- Some friction reading older tutorials; mitigated by the Sprint 0 spike and by preferring official docs.
- Java 25 upgrade is a small, later story.

## Sprint 0 findings (TD-9, 2026-09-24)
- **Pinned:** Spring Boot **4.1.1**, Java **21** (Temurin), Maven 3.9.16 via wrapper 3.3.4.
- **Starters actually used** (Boot 4 names): `spring-boot-starter-webmvc` (was `-web`), `-data-jpa`, `-security`, `-validation`, `-actuator`, `-mail`, `spring-boot-starter-flyway` + `flyway-database-postgresql`, `spring-boot-docker-compose` (runtime, optional), `org.postgresql:postgresql`.
- **Tests:** per-module test starters (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, `-flyway-test`, `-mail-test`, `-validation-test`, `-actuator-test`), `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql` (container class `org.testcontainers.postgresql.PostgreSQLContainer`). Auto-configuration and test-slice annotations moved to per-module packages (for example `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- **springdoc:** `springdoc-openapi-starter-webmvc-ui` 3.1.1 is the Boot 4 line; 2.8.x is Boot 3 only.
- **Problem Details:** `spring.mvc.problemdetails.enabled=true` gives RFC 9457 bodies for MVC exceptions; per-field `errors[]`, stable `type` URIs and `correlation_id` need a small advice extending `ResponseEntityExceptionHandler` (TD-5).
- **Spring Security 7:** lambda DSL only; stateless chain with `httpBasic` for now, resource-server JWT decoding on our own key planned for TD-11 so ADR-0006's OIDC path stays a configuration change.
- **Other versions:** ArchUnit 1.5.0, jqwik 1.10.1, Spotless 3.10.2, JaCoCo 0.8.15, REST Assured 6.0.1, Bucket4j 8.10.1, ShedLock 7.10.1, logstash-logback-encoder 9.0, openapi-diff 2.1.7, Testcontainers 1.21.4 (Boot BOM).
- **Machine:** Homebrew has no `openjdk@21` bottle for Intel Macs on macOS 15, so the JDK is Temurin from Adoptium under `~/Library/Java/JavaVirtualMachines`. Testcontainers on Colima needs `DOCKER_HOST` and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`; the Makefile exports both.
