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

## Sprint 0 findings (TD-9)
_To be filled in: exact Boot version, starter artifact ids used, Security 7 notes, springdoc version._
