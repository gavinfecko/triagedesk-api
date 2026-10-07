# TriageDesk — Architecture

_Living document. Each decision here has, or will get, an ADR in [adr/](adr/). Written 2026-09-24, before Sprint 0; sections marked **(to confirm in Sprint 0)** are pinned down by the spike TD-9._

## 1. System context
```
 ┌──────────────┐   HTTPS/JSON    ┌──────────────────────┐   JDBC    ┌──────────────┐
 │ triagedesk-  │ ──────────────▶ │  triagedesk-api      │ ────────▶ │ PostgreSQL 16│
 │ web (Angular)│ ◀────────────── │  Spring Boot 4 / J21 │           └──────────────┘
 └──────────────┘                 │                      │   SMTP    ┌──────────────┐
 ┌──────────────┐                 │  REST /api/v1        │ ────────▶ │ Mailpit (dev)│
 │ Swagger UI / │ ──────────────▶ │  Swagger UI          │           │ SMTP (prod)  │
 │ curl / k6    │                 │  Actuator            │           └──────────────┘
 └──────────────┘                 └──────────────────────┘
```
One deployable service, one database, one outbound mail channel. No message broker in v1: domain events are in-process Spring `ApplicationEvent`s, and the only scheduled work (SLA breach scan) is a `@Scheduled` job guarded by a database lock so two instances never double-fire.

## 2. Stack
| Concern | Choice | Why (ADR) |
|---|---|---|
| Language / runtime | Java 21 (LTS) | Records, sealed types, pattern matching, virtual threads. What conservative shops run. (ADR-0002) |
| Framework | Spring Boot 4.1.x (Spring Framework 7, Spring Security 7) | Current GA on Spring Initializr at project start. Starters are modular in Boot 4; take names from Initializr, not old tutorials. (ADR-0002) |
| Build | Maven via `./mvnw` wrapper | No global install; what most federal Java teams use. (ADR-0003) |
| Web | Spring MVC, virtual threads on | Blocking style stays simple; virtual threads give the throughput. |
| Persistence | Spring Data JPA (Hibernate) + Flyway + PostgreSQL 16 | Schema is versioned SQL, never `ddl-auto`. Native SQL for reporting queries. (ADR-0004) |
| Auth | Spring Security, stateless JWT (HS256 dev / RS256 prod), rotating refresh tokens in DB, BCrypt | Self-contained, demonstrable, one ADR away from OIDC. (ADR-0006) |
| API docs | springdoc-openapi (Boot 4-compatible line) + Swagger UI | Spec generated from code, snapshot committed, diffed in CI. |
| Validation | Jakarta Bean Validation | Request DTOs validated at the edge; domain invariants enforced again in the aggregate. |
| Mapping | Records for DTOs, hand-written mappers (MapStruct optional) | Small surface; avoids annotation-processor friction on day one. |
| Errors | RFC 9457 Problem Details (`spring.mvc.problemdetails.enabled=true`) | One error shape everywhere. |
| Tests | JUnit 5, AssertJ, Mockito, Spring test slices, Testcontainers (PostgreSQL), ArchUnit, jqwik, RestAssured | See §9. (ADR-0007) |
| Quality | Spotless (palantir-java-format), JaCoCo gate, Dependabot, Trivy, gitleaks | All enforced in CI. |
| Observability | Actuator, Micrometer, Logback JSON, MDC correlation id | Health for the deploy platform; metrics for the dashboard story. |
| Containers | Multi-stage Dockerfile (Temurin JRE 21, non-root), `compose.yaml` | Same image locally and in prod. |
| Deploy | Railway (API + managed PostgreSQL); GitHub Pages for the web client | Already used on other projects; free tier is enough for a demo. |
| CI/CD | GitHub Actions | See §10. |

## 3. Architecture style: modular monolith, package by feature
One Spring Boot application, one deployable, but the code is split into **feature modules** that only talk to each other through their public API (services, DTOs, events). ArchUnit enforces it (TD-8):

```
dev.gavinfecko.triagedesk
├── common        cross-cutting: errors, pagination, clock, correlation id, security helpers
├── identity      User, Role, Auth, RefreshToken            → exposes IdentityService, UserRef
├── ticket        Ticket, Comment, Tag, Audit, StateMachine  → exposes TicketService, events
├── sla           SlaPolicy, BusinessCalendar, SlaTimer      → listens to ticket events, exposes SlaStatus
├── notification  EmailSender, InAppFeed                     → listens to ticket + sla events
└── reporting     KPI queries, CSV export                    → reads via repositories, writes nothing
```
Rules ArchUnit checks: controllers only call services; repositories are only called from their own module; no module imports another module's `internal` package; no cycles between modules; nothing in `sla` or `notification` calls `ticket` synchronously except through `TicketService`.

Inside a module the shape is conventional: `api/` (controller + request/response records), `domain/` (entities, value objects, state machine, domain events), `application/` (services, transactions), `infra/` (repositories, external adapters). (ADR-0005)

## 4. Domain model
```
 User ──< Ticket >── Queue          Ticket >── Category
  │         │  │                    Ticket >── SlaPolicy (via priority at creation)
  │         │  └──< Comment         Ticket ──< AuditEvent
  │         └────< TicketTag >── Tag
  └──< RefreshToken                 Ticket ──1 SlaTimer (first_response, resolution)
 SlaPolicy >── BusinessCalendar ──< Holiday
 User ──< Notification
```
**Ticket** is the aggregate root. Comments, tags, audit events and SLA timers change only through `TicketService`, which is the one place that publishes domain events.

### Ticket fields (v1)
`id (uuid)`, `key (HD-000123, unique, sequence-backed)`, `title`, `description`, `status`, `priority (P1_CRITICAL…P4_LOW)`, `category_id`, `queue_id`, `requester_id`, `assignee_id (nullable)`, `created_at`, `updated_at`, `first_responded_at`, `resolved_at`, `closed_at`, `version (optimistic lock)`.

### Status state machine
| From | To | Who | Guard / side effect |
|---|---|---|---|
| — | `NEW` | Requester, Agent, Admin (create) | SLA timers start. `ticket.created` |
| `NEW` | `OPEN` | Agent, Admin | On assignment or explicit acknowledge. First public agent reply also stops the first-response timer. |
| `OPEN` | `PENDING` | Agent, Admin | Requires a public reply in the same request ("waiting on you because…"). Resolution timer **pauses**. |
| `PENDING` | `OPEN` | Requester (by replying), Agent, Admin | Timer resumes. |
| `OPEN` / `PENDING` | `RESOLVED` | Agent, Admin | Resolution note required. Resolution timer stops. `ticket.resolved` |
| `RESOLVED` | `CLOSED` | Requester (confirm), Admin, or scheduler after 3 business days | `ticket.closed` |
| `RESOLVED` | `OPEN` | Requester, Agent, Admin | Reopen within 14 days of `resolved_at` (anyone; later it is a new ticket); `reopen_count` increments; a fresh resolution timer starts. `ticket.reopened` |
| `NEW` / `OPEN` / `PENDING` | `CANCELLED` | Requester (own ticket, only while `NEW`), Admin | Terminal. Timers cancelled. |
| `CLOSED` / `CANCELLED` | anything | nobody | Terminal. 409 Problem Details `ticket-state-conflict`. |

Implemented as an enum-backed transition table (`TicketStatus.canTransition(to, actorRole)`), tested exhaustively (every from/to/role triple).

### Priority and SLA defaults (seeded, admin-editable)
| Priority | First response | Resolution | Calendar |
|---|---|---|---|
| P1 Critical | 15 min | 4 h | 24×7 |
| P2 High | 1 h | 8 business h | Business hours |
| P3 Medium | 4 business h | 3 business days | Business hours |
| P4 Low | 8 business h | 5 business days | Business hours |

Default business calendar: Mon–Fri 08:00–18:00 `America/New_York`, US federal holidays seeded for the current year.

## 5. SLA engine (the hard part)
- `BusinessCalendar` is a **pure** class: `Duration elapsed(Instant from, Instant to)`, `Instant add(Instant from, Duration businessTime)`. No Spring, no database; the calendar definition is a value object loaded once per policy. Property tests (jqwik): `add(from, elapsed(from, to)) == to` for any `to` inside business hours; `elapsed` is monotonic; holidays contribute zero.
- `SlaTimer` (one per target per ticket): `due_at`, `paused_at`, `paused_total`, `met_at`, `breached_at`. Pausing stores the pause start; resuming shifts `due_at` forward by the business time that elapsed during the pause. All instants in UTC; the calendar's zone is applied only inside `BusinessCalendar`.
- Status is computed, not stored: `on_track` (< 75% of the budget used), `at_risk` (75–100%), `breached` (past due), `met`, `paused`, `cancelled`.
- Breach scan: `@Scheduled(fixedDelay = 60s)` selects timers with `due_at < now AND breached_at IS NULL AND paused_at IS NULL` in one query, marks them breached, publishes `sla.breached` per ticket. A `ShedLock`-style row lock (`sla_scan_lock`) makes the job safe with two instances. Idempotent by construction: a breached timer is never selected twice.
- Escalation on breach (v1): audit event + notification to the assignee and every admin; P3/P4 tickets bump one priority level. Escalation rules are data, not code, so v2 can add "reassign to lead".

## 6. Security model
- **Authentication:** `POST /api/v1/auth/login` → access token (JWT, 15 min, claims `sub`, `role`, `jti`) + refresh token (opaque, 7 days, stored hashed, rotated on every use, family revoked on reuse). `POST /auth/refresh`, `POST /auth/logout`. Passwords BCrypt cost 12, minimum 12 chars, checked against a small breached-password list.
- **Authorization:** roles `ADMIN > AGENT > REQUESTER`, carried in the access token's `role` claim. Authorization is enforced twice: URL-level rules in the security filter chain, and checks in the application services (`@PreAuthorize` for role rules, query-level ownership for "a requester only sees their own tickets"), so a future controller cannot bypass them. `AuthorizationMatrixTest` executes the role column of §7 for every endpoint, both ways, plus anonymous callers.
- **Throttling:** login attempts per account and per IP (Bucket4j in-memory in v1); lockout for 15 min after 10 failures; Problem Details `429` with `Retry-After`.
- **Transport and headers:** HTTPS at the platform edge, HSTS, `X-Content-Type-Options`, CSP for Swagger UI only, CORS allow-list from config.
- **Secrets:** environment variables only; `.env.example` committed; gitleaks in CI and as a pre-commit hook.
- **Threat model (STRIDE, v1):**

| Threat | Where | Control |
|---|---|---|
| Spoofing | Login, token replay | Short access tokens, refresh rotation + reuse detection, BCrypt, throttling |
| Tampering | Ticket fields by wrong role | Service-level authorization, optimistic locking, immutable audit events |
| Repudiation | "I never changed that" | Audit trail with actor, timestamp, before/after |
| Information disclosure | Internal notes to requesters; other users' tickets | Visibility enforced in queries, not just in mappers; tests assert 403/404 |
| Denial of service | Login, search endpoints | Throttling, page size cap 100, query timeouts, indexes |
| Elevation of privilege | Role change | Admin-only, audited, cannot demote the last admin |

## 7. API conventions
- Base path `/api/v1`. Path versioning; a breaking change means `/api/v2` (Spring Framework 7 API versioning is noted as the alternative in ADR-0009 when written).
- JSON, `snake_case` fields, ISO-8601 UTC timestamps, UUIDs as ids, ticket `key` for humans.
- Lists: `?page=0&size=25&sort=created_at,desc` (size capped at 100), response `{ "items": [...], "page": {...} }`.
- Filters are explicit query params (`status`, `priority`, `queue_id`, `assignee_id`, `sla_status`, `tag`, `q` for full-text on title/description via PostgreSQL `tsvector`).
- Writes return the full resource; `PUT`/`PATCH` require `If-Match: "<version>"` → `412` on mismatch.
- Errors are Problem Details with a stable `type` URI per error (`/problems/ticket-state-conflict`), `correlation_id` in every body and in the `X-Correlation-Id` response header.

### Endpoint catalogue (v1)
| Method + path | Role | Story |
|---|---|---|
| `POST /auth/register` `POST /auth/login` `POST /auth/refresh` `POST /auth/logout` | public / any | TD-10..12 |
| `GET /users/me` · `GET/POST/PATCH /users` · `POST /users/{id}/deactivate` | any / ADMIN | TD-11, TD-14 |
| `GET/POST /tickets` · `GET/PATCH /tickets/{key}` | by ownership | TD-20..22, 25 |
| `POST /tickets/{key}/transitions` (`{"to":"PENDING","comment":"..."}`) | per state table | TD-23, 28 |
| `POST /tickets/{key}/assign` · `POST /tickets/{key}/queue` | AGENT / ADMIN | TD-24 |
| `GET/POST /tickets/{key}/comments` (`visibility: PUBLIC\|INTERNAL`) | by role | TD-30 |
| `GET /tickets/{key}/audit` | AGENT / ADMIN (requester: public subset) | TD-31 |
| `PUT /tickets/{key}/tags` · `GET /tags` | AGENT / ADMIN | TD-32 |
| `GET/POST/PATCH /sla/policies` · `GET/PUT /sla/calendars/{id}` | ADMIN | TD-40, 41 |
| `GET /tickets/{key}/sla` | by ownership | TD-42, 44 |
| `GET /notifications` · `POST /notifications/{id}/read` | any | TD-51 |
| `GET /reports/dashboard` · `GET /reports/workload` · `GET /tickets/export.csv` | ADMIN (workload: AGENT too) | TD-60..62 |
| `GET /actuator/health` (public) · `/actuator/info,metrics` (ADMIN) | | TD-7 |

## 8. Data and migrations
- Flyway, `V{n}__{description}.sql`, one migration per story that changes schema, reviewed in the PR, never edited after merge. `R__seed_reference_data.sql` repeatable for categories/queues/policies/holidays.
- Tables (v1): `users`, `refresh_tokens`, `queues`, `categories`, `tickets`, `ticket_comments`, `tags`, `ticket_tags`, `audit_events`, `sla_policies`, `business_calendars`, `calendar_holidays`, `sla_timers`, `sla_scan_lock`, `notifications`, `ticket_key_seq` (sequence).
- Indexes from day one: `tickets(status, priority)`, `tickets(assignee_id)`, `tickets(queue_id, status)`, `tickets(requester_id)`, GIN on `tickets.search_vector`, `sla_timers(due_at) WHERE breached_at IS NULL AND paused_at IS NULL`, `audit_events(ticket_id, created_at)`.
- Every timestamp `timestamptz`; the app sets `TimeZone=UTC` on the connection. `Clock` is injected everywhere so tests control time.
- Seed profile `dev` loads ~60 realistic clinic tickets across all statuses and ages, so the dashboard and SLA badges are meaningful from the first run.

## 9. Testing strategy
| Layer | Tool | What it proves | Runs |
|---|---|---|---|
| Unit | JUnit 5 + AssertJ + Mockito, jqwik | State machine table, `BusinessCalendar`, SLA math, key generator, mappers | every build, < 5 s |
| Slice: web | `@WebMvcTest` + Spring Security test | Every endpoint's status codes, validation, role rules, Problem Details shape | every build |
| Slice: data | `@DataJpaTest` + Testcontainers PostgreSQL | Queries, indexes used (`EXPLAIN` assertions on the hot list query), migrations apply cleanly from empty | every build |
| Integration | `@SpringBootTest` + Testcontainers + RestAssured | Full flows: register → login → create → assign → pending → resolve → close, with SLA and audit asserted | every build |
| Architecture | ArchUnit | Module boundaries, layering, no field injection, no `System.currentTimeMillis` | every build |
| Contract | springdoc export + `openapi-diff` | Committed `docs/openapi.yaml` matches code; breaking changes fail CI unless the PR carries `breaking-change` label | every PR |
| Smoke | Compose up + curl in CI | Image starts, migrations run, health is `UP` | main + release |
| Performance | k6 script, 10k seeded tickets | List endpoint p95 < 200 ms, dashboard < 500 ms | Sprint 4, then on demand |
| Security | gitleaks, Trivy fs + image, Dependabot | No secrets, no known-vulnerable deps | every PR / weekly |

Coverage gate (JaCoCo, enforced in `verify`): 80% line, 70% branch overall; `ticket.domain` and `sla` packages 95% line. Test names read as sentences: `resolvedTicketCanBeReopenedByRequesterWithin14Days()`.

## 10. CI/CD pipeline (GitHub Actions)
```
pull_request → [ lint ] [ build-test (unit+slice+integration, JaCoCo) ] [ security (gitleaks, trivy fs) ] [ openapi-diff ]
push main    → all of the above → [ docker build → push ghcr.io/gavinfecko/triagedesk-api:sha ] → [ compose smoke ]
tag v*       → [ release: gh release --generate-notes, image tagged :vX.Y.Z and :latest ] → [ deploy hook → Railway ]
weekly       → [ dependabot PRs ] [ trivy image rescan ]
```
Branch protection on `main`: PR required, all four PR checks required, linear history, no force-push, admins included. `CODEOWNERS` routes every PR to the maintainer so the review step is explicit even solo.

## 11. Configuration and profiles
`application.yml` with profiles `dev` (Compose PostgreSQL, Mailpit, seed data, Swagger on, HS256 dev key), `test` (Testcontainers, fixed clock), `prod` (env-driven: `DATABASE_URL`, `JWT_PRIVATE_KEY`, `SMTP_*`, `CORS_ORIGINS`; Swagger behind admin; seed off). Fail fast on a missing required property at startup.

## 12. Observability
- Actuator `health` (liveness + readiness groups for the platform), `info` (git sha, build time), `metrics`/`prometheus`.
- Custom Micrometer metrics: `tickets.created`, `tickets.resolved`, `sla.breached` (tagged by priority), `auth.login.failed`.
- JSON logs with `correlation_id`, `user_id`, `ticket_key` in MDC. A request log line per call with status and duration.

## 13. Performance budget
Single small instance, 10k tickets, 100k audit rows: ticket list p95 < 200 ms, ticket detail < 100 ms, dashboard < 500 ms, breach scan < 1 s per minute. Verified by TD-73; regressions caught by the `EXPLAIN` assertions.

## 14. Non-functional requirements
- Startup < 5 s on the JVM; image < 250 MB.
- Zero-downtime migrations only (additive; destructive changes in a later release after code stops reading the column).
- Every list endpoint paginated; every write audited; every error a Problem Details.
- Accessibility and i18n are client concerns and are out of scope for the API in v1.
