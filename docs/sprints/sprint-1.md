# Sprint 1 — 2026-09-29 → 2026-10-05

## Goal
Users can register, log in and open a ticket they can read back, with roles enforced and a realistic demo dataset.

## Planning (Thursday 2026-10-02, three days late)
- **Capacity:** Thursday to Sunday. Monday to Wednesday had no project time (job search), so planning happened on Thursday instead of Monday. The time-box still ends Sunday 2026-10-05.
- **Velocity basis:** Sprint 0 delivered 17 points in one day of mostly mechanical work; this sprint is real domain code, so that number is not a reliable guide. Committing the full 24 points anyway because the stories are small and tightly sequenced; TD-15 is the release valve if time runs short.
- **Committed** (milestone `Sprint 1`), in build order:

| Story | Points | Notes |
|---|---|---|
| TD-10 Register a requester account | 3 | `users` + `audit_events` tables; breached-password check |
| TD-11 Log in, access + refresh tokens | 5 | Resource-server JWT (HS256 dev/test, RS256 prod), `/users/me`, `auth.login.failed` metric |
| TD-12 Refresh rotation, reuse detection, logout | 3 | Family revocation on reuse |
| TD-13 Role-based access control | 3 | Role matrix test over every endpoint; method security on services |
| TD-14 Admin manages users | 3 | Last-admin guard; deactivation revokes refresh tokens |
| TD-20 Create a ticket | 3 | Queues, categories and reference data land here because a ticket needs a category |
| TD-21 View my tickets and a ticket's detail | 2 | Ownership enforced in the service: someone else's ticket is a 404 |
| TD-15 Seed reference data and a demo dataset | 2 | Built last so it can seed real tickets |

- **Carried:** TD-4 (board and branch protection), blocked on a browser login and on repository visibility; no code work.
- **Product Owner amendments at planning:**
  - TD-15: SLA policies and the business calendar are seeded by TD-40/TD-41 in Sprint 3, where their tables are created; "some tickets already breached or at risk" moves there too.
  - TD-15: the demo password becomes `Demo-Password-2026` because `Password123!` is on the breached-password list that TD-10 enforces.
  - TD-10: emails are stored lower-cased in a plain `varchar` with a unique constraint and a lower-case check, instead of `citext`, so Hibernate's `validate` mode can check the column type.

## Daily
| Day | Done since last entry | Next | Blockers |
|---|---|---|---|
| Mon 09-29 | No project time | | |
| Tue 09-30 | No project time | | |
| Wed 10-01 | No project time | | |
| Thu 10-02 | Planning. Dependabot: ArchUnit 1.5.1 and Maven 3.10.0 merged; Spotless 3.10.3 reformatted and merged; Temurin 25 runtime image declined (ADR-0002 pins 21) and major Temurin bumps ignored. All eight stories built and merged one PR each, in order. Real problems found on the way: springdoc documented camelCase while the API speaks snake_case (fixed with a snake_case model resolver and a test); gitleaks flagged a PEM header the RS256 test assembles around a generated key (literal split, branch history rewritten); the Maven bump stored `mvnw.cmd` with CRLF, breaking rebases (#89); `make seed` failed because the security config required MVC beans without a web server (caught only by running the real demo, fixed in TD-15). | Review, `v0.2.0` | Board scope still needs a browser approval |
| Fri 10-03 | | | |
| Sat 10-04 | | | |
| Sun 10-05 | | | |

## Review (brought forward to Thursday 2026-10-02 because the goal was met; the time-box still ends 10-05)
- **Demoed** against the dev database after `make up && make seed && make run`: the nine demo users log in; Rosa (requester) sees only her 10 of the 60 seeded tickets, opens a new one and gets a warning that her `queue_id` was ignored; Kim (requester) gets 404 for Rosa's ticket; Ana (agent) sees all tickets and Rosa's new one, and gets 403 on user administration; the admin lists agents. Every request leaves one access-log line with the user id and correlation id.
- **Release:** `v0.2.0`
- **Not finished:** TD-4 (board and branch protection) — still blocked on a browser approval of the `project` scope and on repository visibility; no code work remains.
- **Accepted by Product Owner:** TD-10 (#84), TD-11 (#86), TD-12 (#87), TD-13 (#88), TD-14 (#90), TD-20 (#91), TD-21 (#92) and TD-15 (#93), each against its acceptance criteria as amended at planning; the PR "Evidence" sections list the tests.

## Retro
| Keep | Stop | Try |
|---|---|---|
| Running the real demo (`make up && make seed && make run` + curl) before calling the sprint done. It found the one bug 123 tests could not: a profile without a web server. | Chaining git commands with `|| true` in scripts that push. One merge with conflict markers reached a Dependabot branch before it was repaired; scripts that push now stop on the first failure. | Add the demo walkthrough as a scripted smoke test (`scripts/demo-smoke.sh`) so CI runs it on every release tag. |
| One story per PR with the executable role matrix growing by a row per endpoint; the matrix caught nothing this sprint, which is the point. | Writing source literals that look like secrets, even in tests. | A change-password endpoint is missing (admins set temporary passwords, users cannot replace them): raise it at Sprint 2 refinement. |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 24 |
| Delivered points (velocity) | 24 |
| Commitment accuracy | 100 % |
| Median cycle time (In Progress → Done) | under one hour per story |
| Coverage (JaCoCo line / branch) | 97.7 % / 89.6 % (gate 80 / 70) |
| Tests | 123 |
| Escaped bugs | 0 found after acceptance; 1 caught before merge by the demo run |
