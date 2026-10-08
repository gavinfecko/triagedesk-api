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
| Tue 10-07 | Planning (#109); TD-32 tags (#110) | TD-33, TD-41 | TD-33's index test passed locally but not in CI: the planner chose a sequential scan for a fresh GIN index |
| Wed 10-08 | TD-33 (#111, index built with `fastupdate = off`), TD-41 (#112), TD-40 (#113), TD-42 (#114), TD-43 (#115), TD-44 (#116), TD-45 (#117), TD-50 (#118); stretch TD-113 (#119) | TD-114, review | Testcontainers' Ryuk container intermittently unreachable on Colima (local only); the 10 000-clock scan test runs over its 1 s budget on a loaded laptop but within it in CI |
| Thu 10-09 – Sun 10-19 | | | |

## Review (brought forward to Wednesday 2026-10-08 because the goal was met; the time-box still ends 10-19)
- **Demoed** with the new scripted demo (`make smoke`, TD-114) against a fresh stack built from the release image: every demo role logs in; Rosa sees only her tickets and gets 404 on someone else's; Ana gets 403 on user admin; Rosa's new ticket arrives with a warning for the field she may not set, an on-track resolution clock and a confirmation email in Mailpit; Ana takes it and it opens; Rosa reads its history; the list finds the seeded breached tickets; full-text search finds the printer tickets. The admin paths (calendar and policy edits, breach escalation, the admin digest, auto-close) were shown through their end-to-end tests, listed in each PR's evidence.
- **Release:** `v0.4.0`
- **Not finished:** nothing. 28 of 28 committed points, plus both stretch stories (TD-113, TD-114).
- **Accepted by Product Owner:** TD-32 (#110), TD-33 (#111), TD-41 (#112), TD-40 (#113), TD-42 (#114), TD-43 (#115), TD-44 (#116), TD-45 (#117), TD-50 (#118), TD-113 (#119), TD-114 (#120), each against its acceptance criteria; the PR "Evidence" sections list the tests. Recorded deviations:
  - TD-43 and TD-50: admins receive one breach digest per scan run instead of one email per breached clock; the assignee still gets one per ticket. Per-clock email to every admin flooded the mail server in the 10 000-clock test and would be noise in a real clinic.
  - TD-44: a clock stores `at_risk_at` so the list can filter on SLA state in SQL; the state itself is still computed, never stored.
  - TD-45: "three business days" counts open days on the ticket's calendar and keeps the time of day (resolved Friday 15:00 on clinic hours closes from Wednesday 15:00).

## Retro
| Keep | Stop | Try |
|---|---|---|
| Stacked branches with a recorded base SHA, rebased with `--onto` after each squash-merge: eleven PRs in two days with the next story always building while the last one was in CI. | Exact-count assertions on data that depends on the wall clock (the seeded at-risk count changed with the time of day) and on fresh-index planner choices. Assert the property that matters, or a floor. | Run the scripted demo on every PR (done in TD-114) and add a check per sprint demo path, so the demo never drifts from the product. |
| Pure domain classes with property tests: the calendar laws caught nothing in review because jqwik had already explored DST weekends and holidays. | Sending side effects (email) synchronously from a transaction's listener: it made the breach scan as slow as the mail server. Side effects that can wait go on their own bounded pool. | Make the local test run independent of Ryuk (`TESTCONTAINERS_RYUK_DISABLED` with explicit cleanup) in the Makefile, since Colima drops its port now and then. |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 28 (+4 stretch) |
| Delivered points (velocity) | 32 (28 committed + 4 stretch) |
| Commitment accuracy | 100 % |
| Median cycle time (PR opened → merged) | about 8 minutes; about an hour per story from first commit to merge |
| Coverage (JaCoCo line / branch) | 98.2 % / 90.4 % (gate 80 / 70; 95 for `ticket.domain` and `sla`) |
| Tests | 260 (+75 this sprint), including 7 500 generated calendar cases per run |
| Escaped bugs | 0 after acceptance; 4 caught before merge (CI-only index plan, time-of-day seed count, mail sender missing in tests, smoke overlay port) |
