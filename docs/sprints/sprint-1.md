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
| Thu 10-02 | Planning. Dependabot: ArchUnit 1.5.1 merged; Spotless 3.10.3 reformatted and merged; Temurin 25 runtime image declined (ADR-0002 pins 21) and major Temurin bumps ignored. | TD-10 → TD-14 | Board scope approval pending in the browser |
| Fri 10-03 | | | |
| Sat 10-04 | | | |
| Sun 10-05 | | | |

## Review (Sunday)
- **Demoed:**
- **Release:** `v0.2.0`
- **Not finished:**
- **Accepted by Product Owner:**

## Retro
| Keep | Stop | Try |
|---|---|---|
| | | |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 24 |
| Delivered points (velocity) | |
| Commitment accuracy | |
| Median cycle time | |
| Coverage | |
| Escaped bugs | |
