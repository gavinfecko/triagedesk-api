# ADR-0003: Maven with the wrapper, not Gradle

**Status:** Accepted · **Date:** 2026-09-24

## Context
Both Maven and Gradle are fine for a Spring Boot service. The machine has no build tool installed, and the intended audience (conservative enterprise Java teams) overwhelmingly uses Maven.

## Decision
Maven, via the committed wrapper (`./mvnw`), so no global install is needed and CI and local builds use the identical version. Plugins: Spring Boot, Surefire/Failsafe (unit vs integration split), JaCoCo, Spotless, springdoc export.

## Consequences
- Verbose XML, but readable to the widest audience.
- Failsafe runs `*IT` tests in `verify`, keeping the unit loop (`test`) fast.
