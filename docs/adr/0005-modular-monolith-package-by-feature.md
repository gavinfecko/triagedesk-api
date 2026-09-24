# ADR-0005: Modular monolith, package by feature, enforced by ArchUnit

**Status:** Accepted · **Date:** 2026-09-24

## Context
Microservices would be the wrong size for one developer and one database, but a flat "controllers / services / repositories" layout hides the domain and lets everything depend on everything.

## Decision
One deployable. Top-level packages are **features** (`identity`, `ticket`, `sla`, `notification`, `reporting`, plus `common`). Each feature has `api`, `application`, `domain`, `infra` sub-packages and an `internal` marker for anything other features may not touch. Features communicate through their application services and through domain events. ArchUnit tests fail the build on violations (cycles, controller → repository, cross-feature `internal` imports, field injection).

## Consequences
- The code reads like the product. A reader finds "SLA" in one place.
- Extracting a feature into its own service later is mechanical, and that is a good interview conversation.
- Slight ceremony (events instead of direct calls between features) that pays back in testability.
