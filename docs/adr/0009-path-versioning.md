# ADR-0009: Path versioning `/api/v1`

**Status:** Accepted · **Date:** 2026-09-24

## Context
Spring Framework 7 adds first-class API versioning (header, query or path based, with version-aware mappings). Path versioning is the most widely understood and works with every client, proxy and Swagger UI without configuration.

## Decision
All endpoints live under `/api/v1`. A breaking change ships under `/api/v2` with the old version kept for at least one release. The `openapi-diff` CI check plus the `breaking-change` label make breaking changes a deliberate act.

## Consequences
- Simple, obvious, cache-friendly.
- If a v2 is ever needed, evaluate Framework 7's native versioning at that point and record a new ADR.
