# ADR-0006: Stateless JWT with rotating refresh tokens, not Keycloak

**Status:** Accepted · **Date:** 2026-09-24

## Context
Options: session cookies, self-issued JWT, or an identity provider (Keycloak, Entra ID) with the API as an OAuth2 resource server. The API must be demonstrable from Swagger UI and consumed by a separate Angular origin; the project should show understanding of token handling, not just configuration of a vendor product.

## Decision
Self-issued **JWT access tokens** (15 min, RS256 in prod, HS256 in dev) and **opaque rotating refresh tokens** stored hashed with family-based reuse detection. Spring Security 7 filter chain, BCrypt (cost 12), roles as a single claim. Throttling and lockout at the service layer.

## Consequences
- Token lifecycle, rotation and reuse detection are implemented and tested here, which is the part worth talking about.
- Switching to an external IdP later is TD-104: keep the resource-server style so the JWT validation can be pointed at an issuer instead of a local key.
- No password reset by email in v1 (admin resets); listed as a follow-up.
