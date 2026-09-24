# TriageDesk — Project Plan

_Created 2026-09-24. Working name. This repo is the Spring Boot API (`triagedesk-api`); the Angular client lives in a second repo (`triagedesk-web`). Both share one GitHub Project board. Other names considered: Deskline, Ticketwell, Helpline._

Companion docs: [ARCHITECTURE.md](ARCHITECTURE.md) (how it is built) · [PROCESS.md](PROCESS.md) (how the work is run) · [BACKLOG.md](BACKLOG.md) (every story) · [adr/](adr/) (decisions) · [sprints/](sprints/) (sprint logs).

## Why this project exists
1. **Close a gap on the resume.** Federal-contractor web roles (the CACI Junior Software Developer posting is the concrete one) name Java Spring, Angular, REST, SQL, Linux, Git and Agile. Every item on that list is already proven in production except Java Spring and Angular. This project is where those two move from "learning" to "built".
2. **Show the workflow, not just the code.** A hiring manager can read a repo like a case file: was the work planned, sliced into stories, reviewed, tested on every change, released with notes? This repo is run the way a real Agile team runs a product, and the whole process is visible on GitHub without needing Jira. See [PROCESS.md](PROCESS.md).

The domain is deliberate. It is the help desk of a two-office medical clinic, which is the IT support job on the resume. Every ticket in the seed data is a real category of request (printer offline, EHR login locked, VPN drop, new-hire laptop, phishing report). The SLA rules are what a small clinic would actually want.

## What it is
A help desk ticketing system for a small IT team: staff open tickets, agents work them from queues, SLA timers count down in business hours, breaches escalate, every change is audited, and an admin sees the numbers. REST API with OpenAPI docs, JWT auth with three roles, PostgreSQL, full test pyramid, CI on every pull request, Docker image on every release, live demo.

### People it serves
| Persona | Who they are | What they need |
|---|---|---|
| **Requester** | Clinic staff (front desk, nurses, billing) | Open a ticket in under a minute, see what is happening with it, reply, confirm it is fixed. |
| **Agent** | IT support (one or two people) | A prioritised queue, clear SLA deadlines, internal notes, reassignment, a way to say "waiting on the user" without the clock running against them. |
| **Admin** | IT lead / practice manager | User and queue management, SLA policy, a dashboard: what is open, what is late, how fast things get fixed. |

### v1 scope (what ships as `v1.0.0` of the API)
| Area | v1 behaviour |
|---|---|
| Identity | Register, log in, refresh, log out. Stateless JWT access tokens (15 min) + rotating refresh tokens (7 days, revocable). Roles `ADMIN`, `AGENT`, `REQUESTER`. Login throttling and lockout. |
| Tickets | Create, read, list/search (paging, filtering, sorting), update priority/category, assign, reassign, queue routing, human-readable keys `HD-1042`, optimistic locking. |
| Lifecycle | State machine `NEW → OPEN → PENDING → RESOLVED → CLOSED`, plus `CANCELLED` and reopen. Every transition has an allowed-actor rule and is tested. |
| Collaboration | Public replies (requester sees) and internal notes (agents only). Tags. |
| Audit | Every change to a ticket is an immutable audit event: who, what, before/after, when. |
| SLA | Policies per priority (first-response and resolution targets), business-hours calendar with holidays and a 24×7 calendar for critical tickets, timers that pause while `PENDING`, breach detection every minute, escalation events, `on_track / at_risk / breached` status on every ticket. |
| Notifications | Email on assignment, reply, resolution and breach (Mailpit in dev, real SMTP in prod). In-app notification feed. |
| Reporting | Dashboard KPIs: open by status/priority/queue, mean time to first response, mean time to resolve, SLA compliance %, agent workload. CSV export of any filtered list. |
| Platform | OpenAPI 3 docs and Swagger UI, RFC 9457 Problem Details errors, correlation IDs, Actuator health/metrics, structured JSON logs, Flyway migrations, seed data, multi-stage Docker image, GitHub Actions CI, GHCR image, live deployment. |

**Out of scope for v1:** attachments, knowledge base articles, webhooks, CSV import, SSO/OIDC, multi-tenancy, i18n, mobile. Every one of those is a named story in the icebox of [BACKLOG.md](BACKLOG.md) so scope stays honest.

### The Angular client (`triagedesk-web`, Sprints 5–6)
Standalone-component Angular app with signals and Angular Material: login, my tickets, ticket detail with reply thread, agent queue with SLA countdown badges, admin pages, dashboard. The API's OpenAPI spec generates the TypeScript client, so the contract is enforced on both sides. Playwright end-to-end tests against the API running in Docker. Deployed to GitHub Pages, pointed at the live API. Its own plan is written at the start of Sprint 5 as a copy of this structure.

## Success criteria (the project is "done" when all of these are true)
- [ ] `v1.0.0` tagged with release notes, and every earlier sprint has a tagged release too.
- [ ] Live demo URL in the README with three demo logins (admin, agent, requester) and seeded tickets.
- [ ] README has a 30-second GIF of the flow: requester opens a ticket, agent replies, SLA badge changes, admin dashboard updates.
- [ ] Test suite: unit + slice + integration (Testcontainers PostgreSQL) + architecture tests, coverage gate ≥ 80% enforced in CI, badge in README.
- [ ] Every pull request went through the template, CI checks, and closed an issue. Board shows ≥ 40 closed stories across ≥ 5 sprints.
- [ ] OpenAPI spec committed and diffed in CI, Swagger UI live.
- [ ] ≥ 8 ADRs recording the real decisions.
- [ ] Angular client consuming the API, deployed, linked from the API README.
- [ ] One line added to the resume Projects section, replacing nothing weaker.

## Release plan
One-week sprints. Sprint 0 is short (setup). Dates are the plan; the sprint log in [sprints/](sprints/) is the record. Velocity is measured from Sprint 1, and later sprints re-commit based on it (see PROCESS.md). If a sprint finishes early, pull the next Ready stories; never shorten the time-box.

| Sprint | Dates | Sprint goal | Release |
|---|---|---|---|
| 0 | Thu 2026-09-25 → Sun 2026-09-28 | Skeleton runs, CI green, board live, error model and docs wired. A `GET /api/v1/health` you can curl. | `v0.1.0` |
| 1 | Mon 2026-09-29 → Sun 2026-10-05 | Users can register, log in and open a ticket they can read back. Roles enforced. Seed data. | `v0.2.0` |
| 2 | Mon 2026-10-06 → Sun 2026-10-12 | Agents can work tickets: search, assign, move through the state machine, reply and note, with an audit trail. | `v0.3.0` |
| 3 | Mon 2026-10-13 → Sun 2026-10-19 | SLA engine: policies, business hours, timers, breach detection, escalation, email. | `v0.4.0` |
| 4 | Mon 2026-10-20 → Sun 2026-10-26 | Dashboard, CSV export, hardening, Docker image, live deployment, README with GIF. | `v1.0.0` |
| 5 | Mon 2026-10-27 → Sun 2026-11-02 | Angular: generated client, auth, my tickets, ticket detail, agent queue. | `web v0.1.0` |
| 6 | Mon 2026-11-03 → Sun 2026-11-09 | Angular: admin, dashboard, Playwright e2e, GitHub Pages, polish. Portfolio page. | `web v1.0.0` |

## Milestones in one line each
1. **Foundation** — a boring, correct skeleton with every quality gate already on. Nothing in later sprints fights the tooling.
2. **Identity + first ticket** — the thinnest end-to-end slice: real auth, real database, real ticket.
3. **Agent workflow** — the state machine and the audit trail are the heart of the product; they get the most tests.
4. **SLA engine** — the hardest logic (business-time arithmetic, pausing, breaches) isolated in pure classes with property-based tests.
5. **Ship** — numbers, hardening, containers, a URL, a GIF.
6. **Client** — prove client-side + server-side in the same stack the job descriptions name.

## Risks and how they are handled
| Risk | Likelihood | Mitigation |
|---|---|---|
| Spring learning curve slows Sprint 1 | High | Sprint 0 includes a time-boxed spike (TD-9). Stories are 1–5 points; an 8 gets split. Official Spring guides over blog posts. |
| Spring Boot 4 differs from most tutorials (renamed starters, Spring Security 7) | High | Dependencies come from Spring Initializr, not copied from articles. ADR-0002 records the version and the known differences. |
| Business-time SLA math is subtle (holidays, pauses, calendars, DST) | Medium | `BusinessCalendar` is a pure class with no Spring dependency, unit tests for every edge, property tests with jqwik, all times stored in UTC with the calendar's zone applied at the edge. |
| Scope creep (this domain invites features) | High | WIP limit of 2, new ideas only enter through the icebox, sprint goal is one sentence and the sprint is judged against it. |
| Job-search time pressure | Medium | Every sprint's committed scope is the minimum demoable increment; stretch stories are marked. The time-box holds even if the scope shrinks. |
| Docker not running (Colima) breaks Testcontainers locally | Medium | `make up` starts Colima if needed; CI uses GitHub's Docker so the suite is always green somewhere. |
| Secrets in history | Low | `.env` gitignored from the first commit, `.env.example` committed, CI secret scan, pre-commit hook. |

## How to talk about this project in an interview
- **What it is:** "A help desk ticketing API in Java 21 and Spring Boot with PostgreSQL, JWT auth, an SLA engine that counts business hours, and an Angular front end. I ran it as a solo Agile project: one-week sprints, user stories on a GitHub Project board, every change through a PR with CI, a tagged release with notes every sprint."
- **Show, don't claim:** open the board (Done column filtered by a sprint), one PR (template filled in, checks green, issue linked), the CI run, the release page, an ADR, the Swagger UI, the live app.
- **The hard part:** the SLA engine. Business-hours arithmetic across holidays and time zones, timers that pause while waiting on the requester, breach detection that must be idempotent when the scheduler runs on two instances. Explain the pure `BusinessCalendar` class and the property tests.
- **Trade-offs to bring up:** modular monolith over microservices (ADR-0005), self-issued JWT over Keycloak (ADR-0006), path versioning over header versioning, optimistic locking over pessimistic.
- **Atlassian mapping:** GitHub Issues = Jira issues, Project board = Jira board with sprints, milestones = Jira sprints/versions, PRs = Bitbucket PRs, `docs/` + ADRs = Confluence, GitHub Actions = Bamboo/Jenkins. The vocabulary translates one-to-one; the discipline is the same.

## Repo layout (target)
```
triagedesk-api/
  .github/            issue + PR templates, CI workflows, dependabot, labels
  docs/               PLAN, ARCHITECTURE, PROCESS, BACKLOG, adr/, sprints/, openapi.yaml
  scripts/            bootstrap-github.sh, backlog_to_issues.py, dev helpers
  src/main/java/dev/gavinfecko/triagedesk/
    common/           errors, correlation id, pagination, time, security helpers
    identity/         users, roles, auth, tokens
    ticket/           ticket aggregate, state machine, comments, tags, audit
    sla/              policies, calendars, timers, breach scheduler
    notification/     email + in-app feed, listeners on domain events
    reporting/        KPI queries, CSV export
  src/main/resources/ application.yml, db/migration/V*.sql, seed data
  src/test/java/      unit, slice, integration (Testcontainers), architecture (ArchUnit)
  compose.yaml        postgres, mailpit, (api in prod profile)
  Dockerfile          multi-stage, non-root, JRE 21
  Makefile            up, down, test, run, fmt, seed
```
