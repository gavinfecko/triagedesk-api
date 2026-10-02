# TriageDesk — Product Backlog

_Source of truth for every story. `scripts/backlog_to_issues.py` turns this file into GitHub Issues (dry-run by default), so the format below is strict. Created 2026-09-24._

**Format contract (the parser depends on it):**
- A story starts with `### TD-<n>: <title>`.
- The next line is the metadata line: `**Epic** E2 · **Points** 3 · **Priority** P1 · **Sprint** 2 · **Area** ticket` (optional `· **Type** task|spike|bug`; default `story`). `**Sprint** icebox` means no milestone.
- Everything until the next `###` or `##` heading is the issue body.
- `Sprint` is the **target** sprint from the release plan. Sprint planning re-commits every Monday from measured velocity; the milestone on the issue is what actually counts.

**Points scale:** 1 trivial · 2 small · 3 a day · 5 two or three days with unknowns · 8 too big, split it.

## Epics
| Epic | Name | Sprint(s) | Outcome |
|---|---|---|---|
| E0 | Foundation | 0 | Skeleton, quality gates, board, error model, docs |
| E1 | Identity & access | 1 (+4) | Register, login, tokens, roles, user admin, throttling |
| E2 | Ticket lifecycle | 1–2 | Create, read, search, state machine, assignment |
| E3 | Collaboration & audit | 2–3 | Replies, internal notes, audit trail, tags, full-text search |
| E4 | SLA engine | 3 | Policies, business calendar, timers, breaches, escalation, auto-close |
| E5 | Notifications | 3–4 | Email, in-app feed |
| E6 | Reporting | 4 | Dashboard KPIs, workload, CSV export |
| E7 | Hardening & delivery | 4 | Security pass, Docker, live deploy, performance, v1.0.0 |
| E8 | Angular client | 5–6 | `triagedesk-web`, own repo, same board |
| — | Icebox | — | Named so scope stays honest |

Target points by sprint: S0 19 · S1 24 · S2 26 · S3 28 · S4 25 (+3 stretch) · S5 19 · S6 13. These are proposals, not commitments.

---

## E0 — Foundation (Sprint 0)

### TD-1: Generate the Spring Boot project skeleton
**Epic** E0 · **Points** 2 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** task

Create the application with Spring Initializr (Maven, Java 21, Spring Boot 4.1.x, group `dev.gavinfecko`, artifact `triagedesk-api`, package `dev.gavinfecko.triagedesk`) with the starters for Spring MVC, Data JPA, Security, Validation, Actuator, Flyway, PostgreSQL driver, Testcontainers and the matching test starters. Take starter names from Initializr; Boot 4 renamed several.

**Acceptance criteria**
- Given a clean clone with JDK 21 and no Maven installed, when `./mvnw -B verify` runs, then the build passes with the generated context-loads test.
- Given `make up` has started PostgreSQL, when `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` starts, then `GET /actuator/health` returns `{"status":"UP"}` within 10 s.
- Given the repo root, then `.editorconfig`, `.gitignore` (Java, Maven, IDE, `.env`), `.env.example` and `Makefile` (`up`, `down`, `run`, `test`, `fmt`) exist.

**Technical notes**
- `spring.threads.virtual.enabled=true`. Lock the Boot version and Java version in `pom.xml` properties; record both in ADR-0002.

### TD-2: Local PostgreSQL and Mailpit with Docker Compose, Flyway baseline
**Epic** E0 · **Points** 2 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given Docker is not running, when `make up` runs, then Colima is started if needed, then `postgres:16` and `axllent/mailpit` come up with a health check, and the command returns only when PostgreSQL accepts connections.
- Given the app starts against that database, then Flyway applies `V1__baseline.sql` (enables `pg_trgm`, sets the database timezone to UTC) and `flyway_schema_history` contains one row.
- Given `spring.jpa.hibernate.ddl-auto=validate`, when an entity and the schema disagree, then startup fails loudly.
- Given `make down`, then containers stop and the named volume is kept; `make nuke` removes it.

### TD-3: CI pipeline with lint, tests, coverage gate and security scan
**Epic** E0 · **Points** 3 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given a pull request, when CI runs, then four jobs report: `lint` (Spotless check), `build-test` (`./mvnw -B verify` with Testcontainers on GitHub's Docker, JaCoCo report uploaded as an artifact, test results published), `security` (gitleaks + Trivy filesystem scan), `openapi-diff` (see TD-6; may be a stub that passes until TD-6 lands).
- Given a push to `main`, then the same jobs run and a Docker image is built and pushed to `ghcr.io/gavinfecko/triagedesk-api:<sha>` (Dockerfile from TD-71 may be a minimal placeholder until then).
- Given Maven dependencies, then they are cached between runs and a cold run finishes in under 8 minutes.
- Given the README, then it shows the CI badge.

### TD-4: Project board, labels, milestones, templates and branch protection
**Epic** E0 · **Points** 2 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given `scripts/bootstrap-github.sh --apply`, then the private repo exists with `main` pushed, every label in `.github/labels.yml` exists, milestones `Sprint 0` … `Sprint 6` exist with due dates from PLAN.md, and the user-level Project **TriageDesk** exists with fields `Points`, `Priority`, `Type`, `Epic` and is linked to the repo.
- Given `scripts/backlog_to_issues.py --apply --project <n>`, then one issue per story in BACKLOG.md exists with the right labels, milestone and body, added to the board, and re-running creates no duplicates.
- Given the `Sprint` iteration field is added by hand in the UI (the CLI cannot create iteration fields), then the *Current sprint* view groups by status.
- Given `main`, then branch protection requires a PR, the four CI checks, linear history and blocks force-pushes, including for admins.
- Given the `.github` folder, then issue templates (story, bug, task, spike), the PR template, `CODEOWNERS` and `dependabot.yml` are in place.

### TD-5: Uniform error model with Problem Details, validation and correlation IDs
**Epic** E0 · **Points** 3 · **Priority** P1 · **Sprint** 0 · **Area** platform

As an API consumer
I want every error to come back in one predictable shape with an id I can quote
So that clients handle failures the same way everywhere and support can find the log line

**Acceptance criteria**
- Given any request, when it is handled, then the response carries `X-Correlation-Id` (echoed from the request header if present, else generated) and every log line for that request includes it.
- Given a request body that fails Bean Validation, when it is posted, then the response is `400` `application/problem+json` with `type: /problems/validation`, a `errors[]` list of `{field, message}`, and `correlation_id`.
- Given an unknown resource, then `404` with `type: /problems/not-found`; given a domain rule violation, then `409` with a type specific to the rule; given an unexpected exception, then `500` with no stack trace or internal message in the body.
- Given a `@WebMvcTest` for the handler, then every branch above is covered.

### TD-6: OpenAPI documentation, Swagger UI and committed spec with diff check
**Epic** E0 · **Points** 2 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given the app in `dev`, then `/swagger-ui.html` renders and `/v3/api-docs.yaml` is served; in `prod` both require the `ADMIN` role.
- Given `./mvnw verify`, then the spec is exported to `docs/openapi.yaml` by an integration test and the build fails if the file changed but was not committed (CI check `openapi-diff`).
- Given a PR whose spec removes or changes an existing operation, then `openapi-diff` fails unless the PR has the `breaking-change` label.
- Given the Problem Details schema from TD-5, then it is referenced by every error response in the spec.

### TD-7: Actuator endpoints and structured JSON logging
**Epic** E0 · **Points** 2 · **Priority** P2 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given the app, then `/actuator/health` is public and exposes `liveness` and `readiness` groups; `/actuator/info` shows git sha and build time; `/actuator/metrics` and `/actuator/prometheus` require `ADMIN`.
- Given the `prod` and `dev` profiles, then logs are one JSON object per line with `timestamp`, `level`, `logger`, `message`, `correlation_id`, `user_id` (when authenticated); `test` keeps plain text.
- Given a request, then exactly one access-log line is written with method, path, status and duration in ms.

### TD-8: Architecture tests, formatting and coverage gate
**Epic** E0 · **Points** 2 · **Priority** P2 · **Sprint** 0 · **Area** platform · **Type** task

**Acceptance criteria**
- Given ArchUnit tests, then the build fails if: a controller calls a repository; a module imports another module's `internal` package; there is a cycle between modules; a class uses field injection; a class calls `Instant.now()`/`LocalDateTime.now()` outside `common.time`.
- Given Spotless with palantir-java-format, then `make fmt` formats and `./mvnw spotless:check` fails on unformatted code.
- Given JaCoCo, then `verify` fails below 80% line / 70% branch overall, and below 95% line in `ticket.domain` and `sla` once those packages exist.

### TD-9: Spike (3 h): Spring Boot 4 starters, Spring Security 7 and springdoc compatibility
**Epic** E0 · **Points** 1 · **Priority** P1 · **Sprint** 0 · **Area** platform · **Type** spike

Time-box 3 hours. Outcome is a comment on this issue and ADR-0002 updated, not shipped code.

**Questions to answer**
- Which starter artifact ids does Initializr give for web MVC, JPA, security, validation, actuator, Flyway, Testcontainers, and the test slices in Boot 4.1?
- What changed in Spring Security 7 for a stateless JWT filter chain (lambda DSL, `SecurityFilterChain`, `oauth2ResourceServer().jwt()` vs a custom filter)?
- Which springdoc-openapi release supports Boot 4, and does its spec export work headless in a test?
- Does `spring.mvc.problemdetails.enabled` cover validation errors out of the box in Framework 7, or is a custom `ResponseEntityExceptionHandler` still needed?

---

## E1 — Identity & access (Sprint 1)

### TD-10: Register a requester account
**Epic** E1 · **Points** 3 · **Priority** P1 · **Sprint** 1 · **Area** identity

As a member of clinic staff
I want to create an account with my work email and a password
So that I can open tickets and follow their progress

**Acceptance criteria**
- Given a valid email, display name and a password of 12+ characters, when I `POST /api/v1/auth/register`, then I get `201` with my user (id, email, display name, role `REQUESTER`) and no password field.
- Given an email that already exists (case-insensitive), then `409 /problems/email-taken` without revealing whether the account is active.
- Given a password shorter than 12 characters or on the small breached-password list, then `400 /problems/validation` naming the `password` field.
- Given registration succeeds, then the password is stored as a BCrypt hash (cost 12) and an `audit_events` row `user.registered` exists.

**Schema:** `users(id uuid pk, email varchar unique (stored lower-case, checked), display_name, password_hash, role, active bool, created_at, updated_at)`.

### TD-11: Log in and receive access and refresh tokens
**Epic** E1 · **Points** 5 · **Priority** P1 · **Sprint** 1 · **Area** identity

As a user
I want to log in with my email and password
So that I can call the API as myself

**Acceptance criteria**
- Given valid credentials, when I `POST /auth/login`, then `200` with `access_token` (JWT, 15 min, claims `sub`, `role`, `jti`, `iat`, `exp`), `refresh_token` (opaque, 7 days), `token_type: Bearer`, `expires_in`.
- Given the access token as `Authorization: Bearer …`, when I `GET /users/me`, then I get my user.
- Given wrong credentials or an inactive account, then `401 /problems/invalid-credentials` with the same body and timing class either way.
- Given a token that is expired, malformed or signed with another key, then `401` and the failure is counted in the `auth.login.failed` metric.
- Given the `dev` profile, then the signing key is HS256 from `.env`; given `prod`, then RS256 with the private key from `JWT_PRIVATE_KEY` and startup fails if it is missing.

**Schema:** `refresh_tokens(id uuid pk, user_id fk, token_hash, family_id, issued_at, expires_at, revoked_at, replaced_by)`.

### TD-12: Refresh with rotation, reuse detection and logout
**Epic** E1 · **Points** 3 · **Priority** P1 · **Sprint** 1 · **Area** identity

As a user
I want my session to continue without logging in every 15 minutes, and to end it when I choose
So that the API stays convenient and my tokens cannot be replayed

**Acceptance criteria**
- Given a valid refresh token, when I `POST /auth/refresh`, then I get a new access token and a new refresh token, and the old refresh token is marked replaced.
- Given a refresh token that was already used, when it is presented again, then `401 /problems/token-reuse`, every token in its family is revoked, and an audit event `auth.token_reuse_detected` is written.
- Given `POST /auth/logout` with a valid access token, then the current refresh family is revoked and a subsequent refresh fails with `401`.
- Given a refresh token older than 7 days, then `401 /problems/token-expired`.

### TD-13: Role-based access control
**Epic** E1 · **Points** 3 · **Priority** P1 · **Sprint** 1 · **Area** identity

As the IT lead
I want requesters, agents and admins to see and do only what their role allows
So that internal notes and admin functions stay internal

**Acceptance criteria**
- Given roles `ADMIN`, `AGENT`, `REQUESTER`, when any endpoint is called, then the security filter chain enforces the role matrix in ARCHITECTURE.md §7 and returns `403 /problems/forbidden` for the wrong role and `401` when unauthenticated.
- Given a requester, when they read a ticket that is not theirs, then `404` (not `403`) so ticket existence is not leaked.
- Given a `@WebMvcTest` per controller, then every endpoint has at least one positive and one negative role test.
- Given method security, then `@PreAuthorize` ownership checks live on the service layer and are covered by tests that bypass the controller.

### TD-14: Admin manages users
**Epic** E1 · **Points** 3 · **Priority** P1 · **Sprint** 1 · **Area** identity

As an admin
I want to list users, create agent accounts, change roles and deactivate people who leave
So that the help desk reflects who actually works here

**Acceptance criteria**
- Given an admin, when they `GET /users?role=AGENT&active=true&page=0&size=25`, then a paged list; given a non-admin, then `403`.
- Given an admin, when they `POST /users` with role `AGENT`, then the account is created with a temporary password flag and an `user.created` audit event.
- Given an admin, when they `PATCH /users/{id}` to change the role, then it is audited; given the target is the last active admin being demoted or deactivated, then `409 /problems/last-admin`.
- Given `POST /users/{id}/deactivate`, then the user cannot log in, their refresh tokens are revoked, and their open tickets keep them as requester (history is never rewritten).

### TD-15: Seed reference data and a realistic demo dataset
**Epic** E1 · **Points** 2 · **Priority** P1 · **Sprint** 1 · **Area** platform · **Type** task

**Acceptance criteria**
- Given the repeatable migration `R__reference_data.sql`, then queues (`Front Desk`, `Clinical Systems`, `Network`, `Hardware`) and categories (`EHR`, `Printer`, `Network/VPN`, `Email/M365`, `Hardware`, `Access request`, `Security incident`, `Other`) exist after startup in every profile. (Amended at Sprint 1 planning: SLA policies and the calendar are seeded by TD-40/TD-41 with their tables.)
- Given the `dev` profile, then `make seed` creates admin `admin@clinic.test`, two agents, six requesters (password `Demo-Password-2026` for all; amended at Sprint 1 planning because `Password123!` is on the breached list) and ~60 tickets spread across every status, priority, queue and age (some already breached, some at risk) so lists and the dashboard mean something (breached and at-risk tickets arrive with the SLA engine in Sprint 3).
- Given the seed runs twice, then nothing is duplicated.

### TD-16: Login throttling and account lockout
**Epic** E1 · **Points** 3 · **Priority** P2 · **Sprint** 4 · **Area** identity

As the IT lead
I want repeated failed logins to slow down and then lock the account
So that password guessing is not practical

**Acceptance criteria**
- Given 5 failed attempts for one account within 5 minutes, when a 6th arrives, then `429 /problems/too-many-attempts` with `Retry-After`.
- Given 10 failures in 15 minutes, then the account is locked for 15 minutes, an audit event `auth.account_locked` is written and the admins are notified.
- Given 30 failures from one IP in 5 minutes across any accounts, then that IP gets `429` regardless of account.
- Given the lockout window passes, then login works again without admin action; given an admin, then `POST /users/{id}/unlock` clears it early.

---

## E2 — Ticket lifecycle (Sprints 1–2)

### TD-20: Create a ticket
**Epic** E2 · **Points** 3 · **Priority** P1 · **Sprint** 1 · **Area** ticket

As a requester
I want to open a ticket with a title, description, category and how urgent it is
So that IT knows what is wrong and can get to it in the right order

**Acceptance criteria**
- Given a requester, when they `POST /tickets` with `title` (5–120 chars), `description` (≤ 5000), `category_id` and `priority`, then `201` with the ticket in status `NEW`, a key like `HD-000123` from a sequence, `requester_id` = me, and `Location` header.
- Given an agent or admin, when they create a ticket, then they may set `requester_id` to another user ("phoned in") and `queue_id`; a requester may not (fields ignored with a `warnings[]` entry, not an error).
- Given no `queue_id`, then the ticket lands in the category's default queue.
- Given creation succeeds, then a `ticket.created` domain event is published (SLA and notifications subscribe later) and an audit event is written.
- Given a missing or invalid field, then `400 /problems/validation`.

**Schema:** `tickets` as in ARCHITECTURE.md §4, `ticket_key_seq`, indexes listed in §8.

### TD-21: View my tickets and a ticket's detail
**Epic** E2 · **Points** 2 · **Priority** P1 · **Sprint** 1 · **Area** ticket

As a requester
I want to see the tickets I opened and the current state of each one
So that I do not have to walk over and ask

**Acceptance criteria**
- Given a requester, when they `GET /tickets`, then only their own tickets, newest first, paged.
- Given a requester, when they `GET /tickets/{key}` for their ticket, then full detail including status, priority, assignee display name, timestamps; for someone else's ticket, then `404`.
- Given an agent or admin, then `GET /tickets/{key}` works for any ticket.
- Given an unknown key, then `404 /problems/not-found`.

### TD-22: List and search tickets with paging, filtering and sorting
**Epic** E2 · **Points** 5 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an agent
I want to filter the ticket list by status, priority, queue, assignee and tag and sort it
So that I can work the right ticket next

**Acceptance criteria**
- Given `GET /tickets?status=OPEN,PENDING&priority=P1_CRITICAL&queue_id=…&assignee_id=me&sort=priority,asc&sort=created_at,asc&page=0&size=50`, then the filters combine with AND, multi-value params with OR, `size` is capped at 100 and `sort` accepts only allow-listed fields (`400` otherwise).
- Given `assignee_id=unassigned`, then tickets with no assignee.
- Given the response, then `{ "items": [...summaries...], "page": { "number", "size", "total_elements", "total_pages" } }`.
- Given a requester, then the same endpoint silently scopes to their own tickets.
- Given 10k seeded tickets, then the query uses the `(status, priority)` or `(queue_id, status)` index (asserted with `EXPLAIN` in a `@DataJpaTest`).

**Technical notes:** JPA Specifications or a small query builder in `ticket.infra`; keep the filter object a record with validation.

### TD-23: Ticket status state machine
**Epic** E2 · **Points** 5 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an agent
I want tickets to move only through allowed statuses, by the people allowed to move them
So that everyone always agrees on where a ticket stands

**Acceptance criteria**
- Given the transition table in ARCHITECTURE.md §4, when `POST /tickets/{key}/transitions` `{ "to": "...", "comment": "..." }` is called, then allowed transitions succeed with `200` and the updated ticket, and every other (from, to, role) combination returns `409 /problems/ticket-state-conflict` naming the current status.
- Given a transition to `PENDING` or `RESOLVED`, then `comment` is required (`400` without it) and is stored as a public comment.
- Given a transition to `OPEN` from `NEW`, then `first_responded_at` is set if null.
- Given `RESOLVED`, then `resolved_at` is set; given `CLOSED`, then `closed_at`; given reopen, then `resolved_at` and `closed_at` are cleared.
- Given a unit test, then it iterates every status × status × role triple and asserts against the table, so the table cannot drift from the code.
- Given a transition, then the matching domain event (`ticket.status_changed` with from/to) is published once.

### TD-24: Assign and reassign tickets; move between queues
**Epic** E2 · **Points** 3 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an agent
I want to take a ticket, hand it to a colleague, or move it to the right queue
So that work lands with the person who can do it

**Acceptance criteria**
- Given an agent, when they `POST /tickets/{key}/assign` `{ "assignee_id": "me" | uuid | null }`, then the assignee changes, a `NEW` ticket becomes `OPEN`, and an audit event records old and new assignee.
- Given an assignee who is not an active agent or admin, then `400 /problems/invalid-assignee`.
- Given `POST /tickets/{key}/queue` `{ "queue_id": … }`, then the queue changes and is audited; given a requester, then `403`.
- Given a closed or cancelled ticket, then both endpoints return `409`.

### TD-25: Change a ticket's priority or category
**Epic** E2 · **Points** 2 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an agent
I want to correct the priority or category a requester chose
So that SLA clocks and queues reflect reality

**Acceptance criteria**
- Given an agent, when they `PATCH /tickets/{key}` with `priority` and/or `category_id` and `If-Match`, then the fields change, are audited with before/after, and a `ticket.priority_changed` event fires when priority changed (the SLA engine recalculates in TD-42).
- Given a requester, when they `PATCH` their own ticket, then only `title` and `description` may change, and only while `NEW`; anything else `403`.
- Given a closed ticket, then `409`.

### TD-26: Optimistic locking with version and ETag
**Epic** E2 · **Points** 2 · **Priority** P2 · **Sprint** 2 · **Area** ticket

As an agent
I want a stale edit to be rejected instead of silently overwriting a colleague's change
So that two people working the same ticket do not lose work

**Acceptance criteria**
- Given `GET /tickets/{key}`, then the response carries `ETag: "<version>"`.
- Given `PATCH /tickets/{key}` with a matching `If-Match`, then `200` and the new `ETag`; with a stale one, then `412 /problems/precondition-failed`; with none, then `428 /problems/precondition-required`.
- Given two concurrent updates in an integration test, then exactly one succeeds.

### TD-27: Requester confirms a fix or reopens a resolved ticket
**Epic** E2 · **Points** 2 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As a requester
I want to say "yes, fixed" or "no, still broken" when IT marks my ticket resolved
So that tickets close on my word, not by default

**Acceptance criteria**
- Given a `RESOLVED` ticket of mine, when I transition to `CLOSED`, then it closes and a `ticket.closed` event fires.
- Given a `RESOLVED` ticket of mine resolved less than 14 days ago, when I transition to `OPEN` with a comment, then it reopens, `reopen_count` increments, and the event is `ticket.reopened`.
- Given more than 14 days, then `409 /problems/reopen-window-closed` with a hint to open a new ticket.

### TD-28: Cancel a ticket
**Epic** E2 · **Points** 1 · **Priority** P2 · **Sprint** 2 · **Area** ticket

As a requester
I want to cancel a ticket I opened by mistake
So that IT does not waste time on it

**Acceptance criteria**
- Given my ticket in `NEW`, when I transition to `CANCELLED`, then it is cancelled, terminal, and its SLA timers are cancelled.
- Given my ticket in any other status, then `409`; given an admin, then any non-terminal ticket can be cancelled with a comment.

---

## E3 — Collaboration & audit (Sprints 2–3)

### TD-30: Public replies and internal notes
**Epic** E3 · **Points** 3 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an agent
I want to reply to the requester on the ticket and also leave notes only my team can see
So that the conversation and the troubleshooting both live on the ticket

**Acceptance criteria**
- Given `POST /tickets/{key}/comments` `{ "body": "...", "visibility": "PUBLIC" | "INTERNAL" }`, then agents and admins may use either visibility; requesters may only post `PUBLIC` on their own tickets (`INTERNAL` → `403`).
- Given `GET /tickets/{key}/comments`, then requesters receive only `PUBLIC` comments, enforced in the query, and a test asserts an internal note never appears in a requester's response.
- Given the first `PUBLIC` comment by an agent on a `NEW`/`OPEN` ticket, then `first_responded_at` is set and `ticket.first_response` fires.
- Given a requester replies on a `PENDING` ticket, then the ticket returns to `OPEN` automatically.
- Given a body over 10,000 chars or empty, then `400`.

**Schema:** `ticket_comments(id, ticket_id fk, author_id fk, visibility, body, created_at)`.

### TD-31: Audit trail for every ticket change
**Epic** E3 · **Points** 3 · **Priority** P1 · **Sprint** 2 · **Area** ticket

As an admin
I want to see who changed what on a ticket and when
So that disputes and post-mortems have facts

**Acceptance criteria**
- Given any state change on a ticket (create, status, assignee, queue, priority, category, title, tags, comment added, SLA breach, escalation), then exactly one `audit_events` row is written in the same transaction with `actor_id` (or `system`), `action`, `field`, `before`, `after` (JSON), `created_at`, `correlation_id`.
- Given `GET /tickets/{key}/audit`, then agents and admins see everything; requesters see only public-facing actions (status, assignee display name, public comments).
- Given an audit row, then it can never be updated or deleted (no update/delete methods on the repository; ArchUnit asserts it).
- Given the seed data, then every seeded ticket has a coherent history.

**Schema:** `audit_events(id bigserial, ticket_id fk null, user_id fk null, actor_id, action, field, before jsonb, after jsonb, correlation_id, created_at)`, index `(ticket_id, created_at)`.

### TD-32: Tags on tickets
**Epic** E3 · **Points** 2 · **Priority** P2 · **Sprint** 3 · **Area** ticket

As an agent
I want to tag tickets (e.g. `recurring`, `vendor`, `phishing`)
So that patterns are visible and lists can be filtered by them

**Acceptance criteria**
- Given `PUT /tickets/{key}/tags` `["recurring","vendor"]`, then the set is replaced, unknown tags are created (lower-cased, trimmed, 2–30 chars), and the change is audited.
- Given `GET /tags`, then all tags with usage counts; given `GET /tickets?tag=recurring`, then filtered.
- Given a requester, then `403` on write and tags are visible on read.

### TD-33: Full-text search on title and description
**Epic** E3 · **Points** 3 · **Priority** P2 · **Sprint** 3 · **Area** ticket

As an agent
I want to search tickets by words in the title or description
So that I can find "the printer thing from last week" fast

**Acceptance criteria**
- Given `GET /tickets?q=printer+jam`, then results ranked by `ts_rank` over a stored `search_vector` (`tsvector`, `english`), combinable with every other filter.
- Given a migration, then `search_vector` is a generated column with a GIN index and an `EXPLAIN` test proves it is used.
- Given a term that appears only in an internal note, then it is not matched (comments are out of scope for v1 search).

---

## E4 — SLA engine (Sprint 3)

### TD-40: SLA policies per priority
**Epic** E4 · **Points** 3 · **Priority** P1 · **Sprint** 3 · **Area** sla

As an admin
I want to set how fast each priority must get a first response and a resolution, and on which calendar
So that the team's promises are explicit and measurable

**Acceptance criteria**
- Given `GET /sla/policies`, then the four seeded policies (ARCHITECTURE.md §4) each with `priority`, `first_response_minutes`, `resolution_minutes`, `calendar_id`, `active`.
- Given an admin, when they `PATCH /sla/policies/{id}`, then the change is audited and applies only to tickets created afterwards (existing timers keep the policy they were created with).
- Given a non-admin, then `403`; given `resolution_minutes` ≤ `first_response_minutes`, then `400`.

**Schema:** `sla_policies(id, priority unique, first_response_minutes, resolution_minutes, calendar_id fk, active, updated_at)`.

### TD-41: Business-hours calendar with holidays
**Epic** E4 · **Points** 5 · **Priority** P1 · **Sprint** 3 · **Area** sla

As an admin
I want SLA clocks to run only during our working hours and to skip holidays
So that a ticket opened Friday at 5 pm is not "late" by Saturday morning

**Acceptance criteria**
- Given a calendar `{ zone: "America/New_York", hours: { MON: ["08:00","18:00"], … }, holidays: [dates] }`, when `BusinessCalendar.elapsed(from, to)` is called, then only in-hours, non-holiday time counts; given the 24×7 calendar, then wall-clock time counts.
- Given `BusinessCalendar.add(from, duration)`, then the result is the instant at which `duration` of business time has passed, landing inside business hours.
- Given a DST transition day in `America/New_York`, then a 10-hour business day still counts 10 hours (tests for both spring-forward and fall-back).
- Given jqwik property tests, then for random `from` and business-hour `to`: `add(from, elapsed(from, to)) == to`, `elapsed` is monotonic in `to`, and holidays contribute zero.
- Given `GET/PUT /sla/calendars/{id}` for admins, then the definition can be read and edited (validated: hours within a day, start < end, zone valid), and edits are audited.
- Given the class, then it has no Spring or JPA dependency (ArchUnit).

**Schema:** `business_calendars(id, name, zone, hours jsonb)`, `calendar_holidays(calendar_id fk, date, name)`.

### TD-42: SLA timers on every ticket
**Epic** E4 · **Points** 5 · **Priority** P1 · **Sprint** 3 · **Area** sla

As an agent
I want each ticket to show when its first response and resolution are due, with the clock paused while I wait on the requester
So that I work to real deadlines

**Acceptance criteria**
- Given `ticket.created`, then two `sla_timers` rows are created (`FIRST_RESPONSE`, `RESOLUTION`) with `due_at` = `calendar.add(created_at, policy minutes)`.
- Given `ticket.first_response`, then the first-response timer gets `met_at` and is never breached afterwards.
- Given a transition to `PENDING`, then the resolution timer records `paused_at`; given `PENDING → OPEN`, then `paused_total` grows by the business time elapsed during the pause and `due_at` shifts by the same amount.
- Given `ticket.priority_changed`, then the resolution timer is recalculated from the original start with the new policy, keeping accumulated pauses; the change is audited.
- Given `RESOLVED`, then the resolution timer gets `met_at` (or stays breached if it already was); given reopen, then a fresh resolution timer starts from the reopen time; given `CANCELLED`, then timers are cancelled.
- Given `GET /tickets/{key}/sla`, then both timers with `due_at`, `status` (`on_track` < 75 % used, `at_risk` ≥ 75 %, `breached`, `met`, `paused`, `cancelled`), `remaining_business_minutes`, `paused_total_minutes`.
- Given the seed data, then some tickets are at risk and some breached on first run.

**Schema:** `sla_timers(id, ticket_id fk, kind, policy_snapshot jsonb, started_at, due_at, paused_at, paused_total_seconds, met_at, breached_at, cancelled_at)`; partial index on `due_at` where open.

### TD-43: Breach detection scheduler and escalation
**Epic** E4 · **Points** 3 · **Priority** P1 · **Sprint** 3 · **Area** sla

As the IT lead
I want breached tickets flagged within a minute and the right people told
So that nothing quietly goes over

**Acceptance criteria**
- Given a `@Scheduled(fixedDelay = 60_000)` job, when it runs, then it selects every open, unpaused timer with `due_at < now`, sets `breached_at`, writes an audit event and publishes `sla.breached` per ticket, in one transaction per batch of 100.
- Given two instances running the job at once, then only one does work (row lock in `sla_scan_lock` with a lease; the other logs and skips), proven by a test that runs the job concurrently.
- Given a breached `RESOLUTION` timer on a P3/P4 ticket, then the ticket's priority is raised one level (audited as `system`) and the assignee and all admins are notified; P1/P2 notify only.
- Given a timer already breached, then it is never selected again (idempotent).
- Given the job, then it finishes in under 1 s with 10k tickets, and its duration is a Micrometer timer `sla.scan`.

### TD-44: SLA status in the ticket list and filters
**Epic** E4 · **Points** 2 · **Priority** P1 · **Sprint** 3 · **Area** sla

As an agent
I want the ticket list to show each ticket's SLA state and let me filter to "at risk" and "breached"
So that the queue sorts itself by what is about to go wrong

**Acceptance criteria**
- Given `GET /tickets`, then each summary includes `sla: { resolution_status, resolution_due_at, first_response_status }`.
- Given `?sla_status=at_risk,breached`, then only those; given `?sort=sla_due,asc`, then soonest due first.
- Given a requester, then they see the same fields on their own tickets (transparency is a feature).

### TD-45: Auto-close resolved tickets after three business days
**Epic** E4 · **Points** 2 · **Priority** P2 · **Sprint** 3 · **Area** sla

As an agent
I want resolved tickets that nobody reopened to close by themselves after three business days
So that the board does not fill with tickets waiting on a "thanks"

**Acceptance criteria**
- Given a `RESOLVED` ticket with `resolved_at` more than 3 business days ago (ticket's calendar), when the hourly job runs, then it transitions to `CLOSED` as actor `system`, audited, with a `ticket.closed` event and a notification to the requester.
- Given a ticket the requester reopened, then the job never touches it.
- Given the job, then it reuses the same lease lock as TD-43.

---

## E5 — Notifications (Sprints 3–4)

### TD-50: Email notifications
**Epic** E5 · **Points** 3 · **Priority** P1 · **Sprint** 3 · **Area** notification

As a requester and as an agent
I want an email when something happens to a ticket I care about
So that I do not have to keep checking

**Acceptance criteria**
- Given `ticket.created`, then the requester gets a confirmation with the key; given `assigned`, then the assignee gets an email; given a `PUBLIC` comment, then the other party gets it; given `resolved`, then the requester gets a "confirm or reopen" email; given `sla.breached`, then the assignee and admins get it.
- Given the `dev` profile, then mail goes to Mailpit and an integration test asserts via Mailpit's API that the right message arrived; given `prod`, then SMTP from env.
- Given the listener, then it runs after the transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`) and a send failure is logged and counted, never failing the request.
- Given templates, then they are plain-text plus a simple HTML version with the ticket key, title, status and a link built from `APP_BASE_URL`.

### TD-51: In-app notification feed
**Epic** E5 · **Points** 2 · **Priority** P2 · **Sprint** 4 · **Area** notification

As a user
I want a list of recent notifications with unread counts
So that the web client can show a bell

**Acceptance criteria**
- Given the same events as TD-50, then a `notifications` row is written for each recipient with `type`, `ticket_key`, `title`, `read_at`.
- Given `GET /notifications?unread=true&page=0&size=20`, then mine only, newest first, with `unread_count`; given `POST /notifications/{id}/read` and `POST /notifications/read-all`, then marked.
- Given 30 days, then a nightly job deletes read notifications older than that.

---

## E6 — Reporting (Sprint 4)

### TD-60: Dashboard KPIs
**Epic** E6 · **Points** 5 · **Priority** P1 · **Sprint** 4 · **Area** reporting

As an admin
I want one call that tells me how the help desk is doing
So that the dashboard is a single screen

**Acceptance criteria**
- Given `GET /reports/dashboard?from=2026-10-01&to=2026-10-31`, then: open tickets by status, by priority, by queue; created and resolved per day in the range; mean and median time to first response and to resolution (business minutes); SLA compliance % for first response and resolution; breached open count; reopen rate; top 5 categories.
- Given the numbers, then they match a hand-computed fixture in an integration test with 25 known tickets.
- Given native SQL, then each query is in `reporting/infra`, uses the existing indexes, and the endpoint answers in < 500 ms on 10k tickets.
- Given a non-admin, then `403`.

### TD-61: Agent workload and queue depth
**Epic** E6 · **Points** 2 · **Priority** P2 · **Sprint** 4 · **Area** reporting

As an agent
I want to see how many open tickets each of us has and how deep each queue is
So that we balance work without a meeting

**Acceptance criteria**
- Given `GET /reports/workload`, then per active agent: open, at risk, breached counts and oldest open ticket age; per queue: depth by priority and unassigned count.
- Given agents and admins, then allowed; requesters `403`.

### TD-62: CSV export of any filtered ticket list
**Epic** E6 · **Points** 2 · **Priority** P2 · **Sprint** 4 · **Area** reporting

As an admin
I want to download the tickets I am looking at as a spreadsheet
So that I can share numbers with the practice manager

**Acceptance criteria**
- Given `GET /tickets/export.csv` with the same filters as TD-22, then a streamed `text/csv` with a header row, UTF-8 BOM, RFC 4180 quoting, and columns key, title, status, priority, category, queue, requester, assignee, created, first response, resolved, SLA status.
- Given 10k rows, then it streams (no full list in memory) and completes in < 3 s.
- Given a requester, then only their own tickets; given a cell starting with `=`, `+`, `-` or `@`, then it is prefixed with `'` (CSV injection).

---

## E7 — Hardening & delivery (Sprint 4)

### TD-70: Security hardening pass
**Epic** E7 · **Points** 3 · **Priority** P1 · **Sprint** 4 · **Area** platform · **Type** task

**Acceptance criteria**
- Given any response, then `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Cache-Control: no-store` on API paths, and a CSP on Swagger UI.
- Given CORS, then only origins from `CORS_ORIGINS` are allowed, credentials off, preflight cached.
- Given the threat model in ARCHITECTURE.md §6, then each row has a test or a config line linked from a checklist in the PR.
- Given Trivy and Dependabot, then no HIGH/CRITICAL findings with a fix available remain; given gitleaks over full history, then clean.
- Given an admin, then role changes and deactivations of themselves are refused.

### TD-71: Production Docker image and Compose production profile
**Epic** E7 · **Points** 3 · **Priority** P1 · **Sprint** 4 · **Area** platform · **Type** task

**Acceptance criteria**
- Given a multi-stage `Dockerfile` (build with the Maven wrapper in a JDK 21 image, run on a Temurin JRE 21 image as a non-root user, layered jar), then the image is < 250 MB and starts in < 5 s.
- Given `docker compose --profile prod up`, then the API, PostgreSQL and Mailpit run together from env vars and `/actuator/health` is `UP`; CI's smoke job does exactly this.
- Given a push to `main`, then the image is pushed to GHCR tagged with the sha; given a tag `v*`, then also `vX.Y.Z` and `latest`.

### TD-72: Live deployment with seed data and demo accounts
**Epic** E7 · **Points** 3 · **Priority** P1 · **Sprint** 4 · **Area** platform · **Type** task

**Acceptance criteria**
- Given Railway (API service from the GHCR image + managed PostgreSQL), then a public URL serves Swagger UI and `/actuator/health`.
- Given the demo environment, then the `dev` seed runs once and three demo logins are documented in the README; a nightly job resets the demo data.
- Given a tag release, then the deploy updates automatically (Railway deploy hook from the release workflow) and the release notes link the URL.
- Given secrets, then they live only in Railway variables; `.env.example` documents every name.

### TD-73: Performance baseline and indexes
**Epic** E7 · **Points** 3 · **Priority** P2 · **Sprint** 4 · **Area** platform · **Type** task

Stretch for Sprint 4; slips to Sprint 5 without affecting `v1.0.0`.

**Acceptance criteria**
- Given `make seed-large` (10k tickets, 100k audit rows, 20k comments), then a k6 script in `scripts/perf/` hits list, detail, search, dashboard at 20 virtual users for 2 minutes.
- Given the run, then p95 list < 200 ms, detail < 100 ms, search < 300 ms, dashboard < 500 ms, error rate 0; the numbers are committed in `docs/perf/2026-10-baseline.md`.
- Given any query over budget, then an index or query change lands in the same PR with a before/after `EXPLAIN`.

### TD-74: v1.0.0 release, README, demo GIF and architecture diagram
**Epic** E7 · **Points** 2 · **Priority** P1 · **Sprint** 4 · **Area** platform · **Type** task

**Acceptance criteria**
- Given the README, then it has: one-paragraph pitch, live demo link with demo logins, 30-second GIF (requester opens → agent replies → SLA badge → dashboard), badges (CI, coverage, release), quick start (`make up && make run`), architecture diagram (from ARCHITECTURE.md, rendered), links to PROCESS, ADRs, board and releases.
- Given `CHANGELOG.md`, then every sprint release is listed.
- Given the GitHub repo, then it is pinned on the profile, has topics (`spring-boot`, `java`, `postgresql`, `rest-api`, `agile`), a description and the website field set.
- Given the resume, then the Projects section has one line for this project only if it beats an existing line.

---

## E8 — Angular client `triagedesk-web` (Sprints 5–6, separate repo, same board)

_Outline. Each becomes a full story in that repo's BACKLOG.md at the start of Sprint 5._

### TD-80: Angular workspace with Material, generated API client, CI and Pages deploy
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 5 · **Area** web · **Type** task

Angular 22 standalone app, signals, Angular Material, `openapi-generator` (`typescript-angular`) from `docs/openapi.yaml` of the API pinned by release tag, Vitest unit tests, ESLint + Prettier, GitHub Actions (lint, test, build), GitHub Pages deploy with the API base URL from an environment file.

### TD-81: Login, logout, token refresh interceptor and route guards
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 5 · **Area** web

### TD-82: My tickets list and create-ticket form
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 5 · **Area** web

### TD-83: Ticket detail with reply thread, transitions and SLA badge
**Epic** E8 · **Points** 5 · **Priority** P1 · **Sprint** 5 · **Area** web

### TD-84: Agent queue with filters, sorting and live SLA countdowns
**Epic** E8 · **Points** 5 · **Priority** P1 · **Sprint** 5 · **Area** web

### TD-85: Admin pages: users, queues, SLA policies and calendar
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 6 · **Area** web

### TD-86: Dashboard with charts
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 6 · **Area** web

### TD-87: Notification bell and feed
**Epic** E8 · **Points** 2 · **Priority** P2 · **Sprint** 6 · **Area** web

### TD-88: Playwright end-to-end suite against the API in Docker
**Epic** E8 · **Points** 3 · **Priority** P1 · **Sprint** 6 · **Area** web · **Type** task

### TD-89: Polish, accessibility pass, README with GIF, portfolio page
**Epic** E8 · **Points** 2 · **Priority** P1 · **Sprint** 6 · **Area** web · **Type** task

---

## Icebox (named so they stay out of v1)

### TD-100: File attachments on tickets and comments
**Epic** E3 · **Points** 5 · **Priority** P3 · **Sprint** icebox · **Area** ticket

Local filesystem in dev, S3-compatible (MinIO) in prod; size and type allow-list; virus scan hook; signed download URLs.

### TD-101: Knowledge base articles linked from resolutions
**Epic** E3 · **Points** 5 · **Priority** P3 · **Sprint** icebox · **Area** ticket

Markdown articles, full-text search, "resolve with article", suggested articles at ticket creation. Mirrors the clinic knowledge base on the resume.

### TD-102: Outbound webhooks on ticket events
**Epic** E5 · **Points** 3 · **Priority** P3 · **Sprint** icebox · **Area** notification

Signed payloads, retries with backoff, delivery log.

### TD-103: CSV import of tickets from another tool
**Epic** E2 · **Points** 3 · **Priority** P3 · **Sprint** icebox · **Area** ticket

### TD-104: OIDC login (Keycloak / Entra ID)
**Epic** E1 · **Points** 5 · **Priority** P3 · **Sprint** icebox · **Area** identity

Replace self-issued JWT with a resource-server configuration; ADR-0006 documents the path.

### TD-105: Per-user notification preferences
**Epic** E5 · **Points** 2 · **Priority** P3 · **Sprint** icebox · **Area** notification

### TD-106: Watchers / CC on a ticket
**Epic** E3 · **Points** 2 · **Priority** P3 · **Sprint** icebox · **Area** ticket

### TD-107: Idempotency keys on ticket and comment creation
**Epic** E2 · **Points** 2 · **Priority** P3 · **Sprint** icebox · **Area** ticket

### TD-108: Canned responses and ticket templates
**Epic** E3 · **Points** 2 · **Priority** P3 · **Sprint** icebox · **Area** ticket

### TD-109: Merge duplicate tickets
**Epic** E2 · **Points** 3 · **Priority** P3 · **Sprint** icebox · **Area** ticket

### TD-110: Satisfaction survey on close
**Epic** E6 · **Points** 3 · **Priority** P3 · **Sprint** icebox · **Area** reporting

### TD-111: Multi-tenant (one deployment, many clinics)
**Epic** E1 · **Points** 8 · **Priority** P3 · **Sprint** icebox · **Area** platform

### TD-112: Internationalisation of notification templates
**Epic** E5 · **Points** 3 · **Priority** P3 · **Sprint** icebox · **Area** notification
