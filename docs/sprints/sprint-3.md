# Sprint 3 — 2026-10-13 → 2026-10-19 (work started 2026-10-07, after Sprint 2 closed early)

## Goal
Tickets carry SLA clocks that count business hours, pause while the requester is being waited on, flag breaches within a minute and escalate; the right people get email; agents can tag tickets and search by words.

## Planning (Tuesday 2026-10-07)
- **Why early:** Sprint 2's goal was met on its first day. The Sprint 3 iteration on the board keeps its dates; work finished before 10-13 still belongs to Sprint 3.
- **Velocity basis:** Sprints 1 and 2 delivered 24 and 26 points. Committing 28; the two retro stories are stretch.
- **Committed** (milestone `Sprint 3`), in build order (dependencies first):

| Story | Issue | Points | Notes |
|---|---|---|---|
| TD-32 Tags on tickets | #28 | 2 | Warm-up; adds the `tag` filter to the list |
| TD-33 Full-text search on title and description | #29 | 3 | Generated `tsvector` column with a GIN index; `q` filter |
| TD-41 Business-hours calendar with holidays | #31 | 5 | Pure `BusinessCalendar` with property tests; calendar tables and admin CRUD; before TD-40 because policies reference calendars |
| TD-40 SLA policies per priority | #30 | 3 | Seeded policies and the default calendar (deferred here from TD-15) |
| TD-42 SLA timers on every ticket | #32 | 5 | Subscribes to the Sprint 2 events; pause on PENDING; seed data gets at-risk and breached tickets |
| TD-43 Breach detection scheduler and escalation | #33 | 3 | Lease lock for two instances; escalation publishes an event TD-50 emails |
| TD-44 SLA status in the ticket list and filters | #34 | 2 | |
| TD-45 Auto-close resolved tickets after three business days | #35 | 2 | Reuses the lease lock |
| TD-50 Email notifications | #36 | 3 | Mailpit in dev, asserted through its API |

- **Stretch:** TD-113 change your own password (#96), TD-114 scripted demo smoke test on release tags (#97).
- **Product Owner notes at planning:** TD-43's "notify the assignee and admins" is delivered as an escalation event here and as email in TD-50; the sprint is accepted only with both. Issue numbers are now written next to every story because they differ from the story numbers (a Sprint 2 lesson).

## Daily
| Day | Done since last entry | Next | Blockers |
|---|---|---|---|
| Tue 10-07 | Planning | TD-32, TD-33 | |
| Wed 10-08 | | | |
| Thu 10-09 | | | |
| Fri 10-10 | | | |
| Sat 10-11 | | | |
| Sun 10-12 | | | |
| Mon 10-13 – Sun 10-19 | | | |

## Review
- **Demoed:**
- **Release:** `v0.4.0`
- **Not finished:**
- **Accepted by Product Owner:**

## Retro
| Keep | Stop | Try |
|---|---|---|
| | | |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 28 (+4 stretch) |
| Delivered points (velocity) | |
| Commitment accuracy | |
| Median cycle time | |
| Coverage | |
| Escaped bugs | |
