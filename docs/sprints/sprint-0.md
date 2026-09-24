# Sprint 0 — 2026-09-24 → 2026-09-28

## Goal
The skeleton runs against PostgreSQL with every quality gate on, CI is green on a pull request, the board is live with the whole backlog on it, and `GET /actuator/health` can be curled.

**Outcome:** met on the first day, except the board, which is blocked outside the code (see TD-4).

## Planning (2026-09-24, before starting)
- **Capacity:** planned as four part-time days (Thu–Sun); work started Wednesday evening instead.
- **Velocity basis:** unknown (first sprint); committed 19 points as a deliberate over-reach for a setup sprint where most stories are mechanical. Anything not Done rolls into Sprint 1 ahead of TD-10.
- **Committed** (milestone `Sprint 0`):

| Story | Points | Notes |
|---|---|---|
| TD-9 Spike: Boot 4 starters, Security 7, springdoc (3 h) | 1 | First. Findings go into ADR-0002 before TD-1 starts. |
| TD-1 Generate the Spring Boot skeleton | 2 | JDK 21; Maven wrapper from Initializr |
| TD-2 Compose PostgreSQL + Mailpit, Flyway baseline | 2 | `make up` starts Colima |
| TD-4 Board, labels, milestones, templates, protection | 2 | bootstrap script + issue import |
| TD-3 CI pipeline | 3 | First real PR on the repo |
| TD-5 Problem Details, validation, correlation IDs | 3 | First code with tests |
| TD-6 OpenAPI + Swagger + spec diff | 2 | |
| TD-8 ArchUnit + Spotless + JaCoCo gate | 2 | |
| TD-7 Actuator + JSON logs | 2 | |

- **Stretch:** none. Sprint 1 starts Monday regardless.

## Daily
| Day | Done since last entry | Next | Blockers |
|---|---|---|---|
| Wed 09-24 | Plan, architecture, process, backlog and ADRs written and pushed. Repo created (private), 26 labels, 7 milestones, 68 issues imported. TD-9 spike answered from the generated `pom.xml` and Maven Central metadata. TD-1 skeleton green on Testcontainers (4 tests). TD-2 Compose + Flyway V1 green after a real bug: the JDBC driver pushes the JVM zone onto every session, fixed with `connection-init-sql`. TD-3 CI green after three iterations (bad Trivy tag, stale action majors, gitleaks needing API access; now runs from its container). TD-5 error model green first time (10 tests). TD-6 green after two real bugs the tests caught (springdoc drops schemas added on the `OpenAPI` bean; test-only endpoints leaked into the exported contract). TD-7 green after one (Boot disables metrics exporters in tests → `@AutoConfigureMetrics`). TD-8 green after the coverage gate's first run reported 66.7 % branch and pointed at six untested error paths, all now tested (47 tests, 99 % line / 83 % branch). Bug #76 filed against TD-5 (parameter-level validation shape) and fixed the same day. | Fix #76, tag `v0.1.0`, sprint review and retro. | TD-4: Project board needs the `project` OAuth scope (browser login); branch protection needs a public repo. Both outside the code. |
| Thu 09-25 | | | |
| Fri 09-26 | | | |
| Sat 09-27 | | | |
| Sun 09-28 | | | |

## Review (brought forward to 2026-09-24 because the goal was met; the sprint time-box still ends 09-28)
- **Demoed:** `make up && make run` → Swagger UI at `/swagger-ui.html` (public in dev) with the `ProblemDetail` schema and the common error responses on every operation; `/actuator/health` public and `UP`; `/actuator/info` for admins with build and git metadata; `/actuator/prometheus` scrape; every error a Problem Details with a correlation id that also appears in the response header and in every log line; JSON logs in dev.
- **Release:** `v0.1.0`
- **Not finished:** TD-4 — labels, milestones, templates and all 68 issues are done; the board and branch protection are blocked on a browser login and on repository visibility. Carried into Sprint 1 with the `blocked` label; no code work remains.
- **Accepted by Product Owner:** TD-1 (#70), TD-2 (#72), TD-3 (#71), TD-5 (#73), TD-6 (#74), TD-7 (#75), TD-8, TD-9 (#69), each against the acceptance criteria in its issue; the PR "Evidence" sections carry the test names.

## Retro
| Keep | Stop | Try |
|---|---|---|
| Writing the test before trusting the framework: every real bug this sprint (session time zone, dropped OpenAPI schema, leaked test endpoints, exporters off in tests) was caught by a test written from the acceptance criteria, not by reading docs. | Pinning GitHub Action versions from memory. Three of the first CI failures were version tags that did not exist or were two majors behind. Look them up (`gh api repos/<action>/releases/latest`) before pinning. | Stacked branches for consecutive stories (branch the next story from the previous one, `git rebase --onto origin/main` after the squash-merge) so a story's build runs while the previous one is in CI. Worked for TD-7 and TD-8; keep it to two levels. |
| One story per PR with the template filled in, even without branch protection. The PR bodies are now the sprint's evidence. | Assuming Boot 3 package names in Boot 4 tests; take them from the jars (`unzip -l`). | Run `make docs` before every push once endpoints exist, so `openapi-diff` never fails on a stale file. |

Follow-ups filed: #76 (parameter-level validation shape). Changes to PROCESS.md: none.

## Metrics
| Metric | Value |
|---|---|
| Committed points | 19 |
| Delivered points (velocity) | 17 (TD-4's 2 points carried; its code is done) |
| Commitment accuracy | 89 % |
| Median cycle time (In Progress → Done) | under one day for every story |
| Coverage (JaCoCo line / branch) | 99 % / 83 % (gate 80 / 70) |
| Escaped bugs | 1 (#76, from TD-5; fixed in Sprint 0) |
