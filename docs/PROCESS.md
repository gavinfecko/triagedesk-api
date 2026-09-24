# TriageDesk — How the work is run

_This is the Agile operating model for the project. It is written so a reader can audit the process from GitHub alone: every rule here leaves a trace (an issue, a board column, a PR check, a release, a sprint log). Created 2026-09-24._

## 1. Principles
1. **Working software every sprint.** Each sprint ends with a tagged release that can be demoed in under five minutes. If the scope has to shrink, it shrinks; the time-box never moves.
2. **Small, finished, reviewed.** Stories are 1–5 points. Work in progress is capped at 2. Nothing is "done" until it meets the Definition of Done in §8.
3. **Everything is visible.** Plans live in `docs/`, work lives in GitHub Issues and the Project board, decisions live in ADRs, history lives in releases. No private to-do lists.
4. **Process serves the product.** Any ceremony that stops earning its time gets changed in the retro, and the change is written down here.

## 2. Roles
One person plays three roles and keeps them separate on purpose, because the tension between them is where good decisions come from.
| Role | Responsibilities | Where it shows on GitHub |
|---|---|---|
| **Product Owner** | Owns the backlog order and the sprint goal. Writes stories in user terms with acceptance criteria. Accepts or rejects finished work against the criteria. | Issue author, backlog priority labels, "accepted" comment closing a story |
| **Scrum Master** | Runs the cadence. Keeps the board honest (WIP limit, stale cards). Runs the retro and updates this document. | `docs/sprints/sprint-N.md`, changes to PROCESS.md |
| **Developer** | Pulls from the top of Ready, delivers to the Definition of Done, opens PRs, writes ADRs when a decision is made. | Branches, PRs, CI, ADRs |

## 3. Cadence
- **Sprint length:** 1 week, Monday to Sunday. Sprint 0 is a short setup sprint (Thu–Sun).
- **Monday:** Sprint planning (30 min) → `docs/sprints/sprint-N.md` created with the goal and the committed stories, milestone `Sprint N` assigned.
- **Every working day:** Daily (5 min) → three lines appended to the sprint log: done since last entry, next, blockers.
- **Wednesday:** Backlog refinement (20 min) → next sprint's candidate stories meet the Definition of Ready; points reviewed.
- **Sunday:** Sprint review (demo, release tag, release notes) then retro (15 min) → both recorded in the sprint log; velocity and cycle time noted.

## 4. The board
GitHub Projects (v2), one board named **TriageDesk** at the user level so it can span `triagedesk-api` and `triagedesk-web`.

| Column | Meaning | Exit rule |
|---|---|---|
| **Backlog** | Every known story, ordered by the Product Owner. | Meets the Definition of Ready → Ready |
| **Ready** | Refined, pointed, could start today. Never more than ~2 sprints' worth. | Pulled at planning or when WIP allows |
| **In Progress** | Branch exists. **WIP limit 2.** | PR opened → In Review |
| **In Review** | PR open, CI running, self-review checklist done. | PR merged and story accepted → Done |
| **Done** | Merged to `main`, accepted against acceptance criteria, in a release or waiting for the sprint's tag. | Never leaves |

Custom fields: `Points` (number), `Sprint` (iteration, 1 week), `Priority` (P0–P3), `Epic` (single select), `Type` (story/bug/task/spike). Views: *Current sprint* (grouped by status), *Backlog by epic*, *Velocity* (Done grouped by sprint, sum of points).

## 5. Labels
| Label | Use |
|---|---|
| `type:story` `type:bug` `type:task` `type:spike` | What kind of work. Every issue has exactly one. |
| `epic:E0-foundation` … `epic:E8-web` | Which epic (see BACKLOG.md). |
| `priority:P0` (drop everything) `P1` (this sprint) `P2` (soon) `P3` (someday) | Product Owner's ordering signal. |
| `area:identity` `area:ticket` `area:sla` `area:notification` `area:reporting` `area:platform` | Code area, drives CODEOWNERS and release-note grouping. |
| `breaking-change` | Allows the `openapi-diff` check to pass on a PR that intentionally breaks the API. |
| `blocked` | Waiting on something outside the story; the reason is in the last comment. |
| `good first issue` | Kept for the day the repo is public and someone else wants to contribute. |

`scripts/bootstrap-github.sh` creates every label from `.github/labels.yml`.

## 6. Stories
Written in the `user_story` issue template. Key `TD-nn` in the title (so the backlog file, the issue, the branch, the commit and the release note all share one handle).

```
TD-23 Ticket status state machine

As an agent
I want tickets to move only through allowed statuses
So that the team and the requester always agree on where a ticket stands

Acceptance criteria
- Given an OPEN ticket, when an agent transitions it to PENDING with a comment, then status is PENDING and the resolution timer is paused
- Given a CLOSED ticket, when anyone requests a transition, then the API returns 409 ticket-state-conflict
- ...

Technical notes
- Transition table in TicketStatus; exhaustive test over (from, to, role)

Definition of Done: the checklist in the template
```
- **Points** use the Fibonacci scale 1, 2, 3, 5, 8. An 8 is a signal to split. Points measure relative effort and uncertainty, not hours.
- **Bugs** are not pointed; they are fixed in the sprint they are found in if they block the goal, otherwise triaged into the backlog.
- **Spikes** are time-boxed (the box is in the title) and produce a written outcome (comment or ADR), never code that ships.

## 7. Definition of Ready (a story can enter Ready when)
- [ ] Written as *As a / I want / So that* from a real persona.
- [ ] At least three acceptance criteria in Given/When/Then, including one failure case.
- [ ] Pointed; 5 or less.
- [ ] Dependencies named (issue links) and not blocked.
- [ ] Any schema change described (tables/columns) so the migration can be reviewed.
- [ ] Any new endpoint listed with method, path, roles.

## 8. Definition of Done (a story is Done when)
- [ ] Every acceptance criterion has at least one automated test that fails without the change.
- [ ] Unit + slice + integration tests as appropriate; the coverage gate is green.
- [ ] Migration (if any) applies from empty and on top of the previous version; seed data updated.
- [ ] `docs/openapi.yaml` regenerated; `openapi-diff` check green (or `breaking-change` label justified in the PR).
- [ ] Problem Details for every new error path; role rules tested (positive and negative).
- [ ] Docs touched: ARCHITECTURE.md if the design changed, ADR if a decision was made, README if the user-facing surface changed.
- [ ] PR used the template, linked the issue with `Closes #n`, all checks green, squash-merged with a conventional title.
- [ ] Demoable from a fresh `make up && make seed`.
- [ ] Product Owner accepted it with a comment referencing the acceptance criteria.

## 9. Branching, commits, pull requests
- **Trunk-based.** `main` is always releasable and protected (PR + checks required, linear history, no force-push).
- **Branches** are short-lived (< 2 days) and named `feat/TD-23-ticket-state-machine`, `fix/TD-31-audit-null-actor`, `chore/ci-cache`, `docs/adr-0006`.
- **Commits** follow Conventional Commits: `feat(ticket): enforce status transition table (TD-23)`. Squash-merge, so the PR title becomes the commit on `main` and must follow the same convention. The `feat`/`fix`/`docs`/`chore`/`refactor`/`test`/`ci`/`perf` prefix drives release-note grouping.
- **Pull requests**: one story per PR, template filled in, screenshots or curl output for anything user-visible, `Closes #n`. Self-review checklist (in the template) is completed before requesting review. CI must be green. Review happens on the PR even when the reviewer is the author: read the diff top to bottom in the GitHub UI, leave comments, resolve them, then merge. Draft PRs are welcome for early CI runs.
- **Hotfix flow:** branch `hotfix/...` from `main`, PR, release patch tag `vX.Y.Z+1`, note in the sprint log.

## 10. CI gates (all required on every PR)
| Check | Fails when |
|---|---|
| `lint` | Spotless finds unformatted code; Checkstyle-equivalent ArchUnit rules fail |
| `build-test` | Any test fails; JaCoCo below 80% line / 70% branch (95% in `ticket.domain` and `sla`) |
| `security` | gitleaks finds a secret; Trivy finds a HIGH/CRITICAL vulnerability with a fix available |
| `openapi-diff` | The generated spec has a breaking change and the PR lacks the `breaking-change` label |

## 11. Releases and versioning
- Semantic versioning. `v0.N.0` at the end of every sprint from Sprint 0; `v1.0.0` when the v1 scope in PLAN.md is complete.
- `gh release create vX.Y.Z --generate-notes` after the sprint review; the notes are edited to lead with the sprint goal and a "What you can do now" paragraph, then the generated PR list grouped by type.
- `CHANGELOG.md` mirrors the release notes (Keep a Changelog format) so the history reads offline too.
- Tags build and push the Docker image `ghcr.io/gavinfecko/triagedesk-api:vX.Y.Z` and trigger the deploy.

## 12. Metrics (recorded every sprint in the sprint log)
| Metric | How measured | Why |
|---|---|---|
| Velocity | Sum of points in Done for the sprint | Plans the next sprint's commitment (use the average of the last 3) |
| Commitment accuracy | Committed points vs delivered | Are stories sized honestly? |
| Cycle time | In Progress → Done, median, from the board's date fields | Catches stories that are too big |
| Coverage | JaCoCo total from CI | Trend, not a target to game |
| Escaped bugs | Bugs found after a story was accepted | Quality of acceptance criteria |

## 13. Sprint log format
`docs/sprints/sprint-N.md` (template in `docs/sprints/TEMPLATE.md`): goal, capacity, committed stories with points, the daily lines, the review (what was demoed, release tag, what was not finished and why), the retro (keep / stop / try, each with an owner and a follow-up issue if needed), and the metrics table.

## 14. Jira / Atlassian translation
| Jira / Atlassian | Here |
|---|---|
| Epic | `epic:*` label + a section in BACKLOG.md |
| Story / Bug / Task / Spike | Issue with `type:*` label from the matching template |
| Story points, Sprint field | Project fields `Points`, `Sprint` |
| Sprint / Board / Backlog | Project iteration + board view / Backlog column |
| Fix version / Release | Milestone `Sprint N` + Git tag + GitHub Release |
| Definition of Ready / Done | §7 and §8, enforced by the issue and PR templates |
| Confluence page | `docs/*.md` and ADRs in the repo, reviewed in PRs like code |
| Bitbucket PR + Bamboo / Jenkins | GitHub PR + GitHub Actions |
| Jira automation | GitHub Actions + `Closes #n` auto-transition |

## 15. Command cheat-sheet
```bash
# start a story
gh issue develop 42 --name feat/TD-23-ticket-state-machine --checkout
# open the PR from the template
gh pr create --fill --body-file .github/PULL_REQUEST_TEMPLATE.md
# watch CI
gh pr checks --watch
# merge (squash) once green and reviewed
gh pr merge --squash --delete-branch
# end of sprint
git tag -a v0.3.0 -m "Sprint 2: agents can work tickets" && git push --tags
gh release create v0.3.0 --generate-notes --title "v0.3.0 — Agents can work tickets"
# board
gh project item-list <number> --owner gavinfecko --format json
```
