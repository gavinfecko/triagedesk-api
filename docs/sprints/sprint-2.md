# Sprint 2 — 2026-10-06 → 2026-10-12

## Goal
Agents can work tickets: move them through the status state machine, assign them, reply and leave internal notes, correct priority and category without overwriting each other, and search the queue, with a full audit trail behind every change.

## Planning (drafted Thursday 2026-10-02, work started Tuesday 2026-10-07)
- **Capacity:** Tuesday to Sunday. The plan was drafted early because Sprint 1 finished on its first day; no project time Friday to Monday.
- **Velocity basis:** Sprint 1 delivered 24 points. Committing the planned 26.
- **Committed** (milestone `Sprint 2`), in build order (dependencies first):

| Story | Points | Notes |
|---|---|---|
| TD-30 Public replies and internal notes | 3 | Comments table; first agent reply sets `first_responded_at`; requester reply reopens a PENDING ticket |
| TD-23 Ticket status state machine | 5 | One transition table, tested over every (from, to, role); PENDING/RESOLVED need a comment, stored as a public reply |
| TD-24 Assign, reassign, move queue | 3 | Assigning a NEW ticket opens it |
| TD-26 Optimistic locking with ETag / If-Match | 2 | Before TD-25 so PATCH is born with preconditions |
| TD-25 Change priority or category | 2 | Requesters may fix their own title/description while NEW |
| TD-27 Confirm a fix or reopen | 2 | 14-day reopen window, reopen count |
| TD-28 Cancel a ticket | 1 | Requester while NEW; admin any open ticket, with a comment |
| TD-31 Audit trail for every ticket change | 3 | Read endpoint with requester filtering; append-only enforced by the database |
| TD-22 List and search with filters and sorting | 5 | Index-backed; `tag` filter waits for TD-32 (Sprint 3) |

- **Raised at Sprint 1 retro, added to the backlog, not committed:** TD-113 change your own password; TD-114 scripted demo smoke test on release tags.
- **Board:** `scripts/board-status.sh <issue> "<column>"` moves a card; GitHub's built-in workflow moves closed issues to Done.

## Daily
| Day | Done since last entry | Next | Blockers |
|---|---|---|---|
| Thu 10-02 | Plan drafted; TD-113 and TD-114 added from the Sprint 1 retro | | |
| Fri 10-03 – Mon 10-06 | No project time | | |
| Tue 10-07 | Planning confirmed | TD-30, TD-23 | |
| Wed 10-08 | | | |
| Thu 10-09 | | | |
| Fri 10-10 | | | |
| Sat 10-11 | | | |
| Sun 10-12 | | | |

## Review (Sunday)
- **Demoed:**
- **Release:** `v0.3.0`
- **Not finished:**
- **Accepted by Product Owner:**

## Retro
| Keep | Stop | Try |
|---|---|---|
| | | |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 26 |
| Delivered points (velocity) | |
| Commitment accuracy | |
| Median cycle time | |
| Coverage | |
| Escaped bugs | |
