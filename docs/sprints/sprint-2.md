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
| Tue 10-07 | Planning confirmed. All nine stories built and merged in dependency order, one PR each (#99, #100, #101, #102, #103, #104, #105, #106, #107). Branch protection is now live, so every merge needed a green, up-to-date branch: one PR was refused until it was rebased because `main` had moved. Real findings on the way: the role-matrix test caught a requester reaching body validation on `/queue` before the staff check (fixed with a URL rule); springdoc documented a `JsonNode` field as Jackson's internals in machine-dependent order, which made the committed spec unreproducible (now mapped to a free-form value); Spring splits `sort=priority,asc` on the comma when binding a list (read raw); PostgreSQL prefers the created-at index for a limited, ordered page even when a filter index exists (the index test now checks what the two page queries actually do). Demo run against the dev database passed: queue view, take, internal note hidden from the requester, pending with a required comment, requester reply reopens, ETag 428/200/412, resolve, confirm, audit trail 12 rows for staff and 11 for the requester, closed is terminal, requester scoping. | Review, `v0.3.0` | |
| Wed 10-08 | | | |
| Thu 10-09 | | | |
| Fri 10-10 | | | |
| Sat 10-11 | | | |
| Sun 10-12 | | | |

## Review (brought forward to Tuesday 2026-10-07 because the goal was met; the time-box still ends 10-12)
- **Demoed** against the dev database after `make up && make seed && make run`: Ana (agent) lists unassigned NEW/OPEN tickets sorted by priority, takes the top P1 (it opens and records the first response), leaves an internal note, parks it PENDING with a question (and is refused without one); Rosa (requester) sees only the public reply, answers, and the ticket returns to OPEN; Ana's PATCH without `If-Match` is 428, with it 200, with the old ETag 412; Ana resolves, Rosa confirms, the ticket closes and refuses further work; the audit trail shows 12 rows to staff and 11 to Rosa; Rosa's searches never show anyone else's tickets.
- **Release:** `v0.3.0`
- **Not finished:** nothing; 26 of 26 points.
- **Accepted by Product Owner:** TD-30 (#99), TD-23 (#100), TD-24 (#101), TD-26 (#102), TD-25 (#103), TD-27 (#104), TD-28 (#105), TD-31 (#106), TD-22 (#107), each against its acceptance criteria; the PR "Evidence" sections list the tests. One deviation recorded in #106: audit immutability is enforced by a database trigger instead of an ArchUnit rule, because the trigger holds even for direct SQL.

## Retro
| Keep | Stop | Try |
|---|---|---|
| Stacked branches: the next story builds on the previous branch while its PR is in CI, then `git rebase --onto main` after the squash-merge. Nine stories went through in one day with no idle time. | Patching code with scripts that assume exact text: Spotless re-wraps lines between stories, and two patches silently half-applied. Patches now locate by position or fail loudly, and never run Maven after a failed patch. | Run `make docs` (spec regeneration) as part of the pre-push check, so a rebase onto a newer `main` never carries a stale spec into `openapi-diff`. |
| The executable role matrix and the demo run: each found a real gap this sprint (the `/queue` validation-before-authorization order; nothing in the demo, which is also information). | Merging straight after the first green run when another PR merged meanwhile: branch protection rightly refuses it. Rebase first, then merge. | Document `JsonNode`-typed API fields with `@Schema` at the field, not only globally, when the next free-form value appears. |

## Metrics
| Metric | Value |
|---|---|
| Committed points | 26 |
| Delivered points (velocity) | 26 |
| Commitment accuracy | 100 % |
| Median cycle time (In Progress → Done) | about one hour per story |
| Coverage (JaCoCo line / branch) | 98.5 % / 91.3 % (gate 80 / 70) |
| Tests | 185 |
| Escaped bugs | 0 after acceptance; 2 caught by tests before merge (authorization order, spec stability) |
