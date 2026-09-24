# ADR-0008: GitHub Issues, Projects and Actions as the Agile toolchain

**Status:** Accepted · **Date:** 2026-09-24

## Context
The goal is a visible, auditable Agile process. Jira and Confluence are what job descriptions name, but they would hide the process behind a login and add tooling for one person. Everything they provide has a GitHub equivalent that a hiring manager can open from the repo.

## Decision
- **Issues** are stories, bugs, tasks and spikes, created from templates, keyed `TD-nn` in the title.
- **Projects (v2)** is the board: Backlog / Ready / In Progress / In Review / Done, fields Points, Sprint (iteration), Priority, Type, Epic; one user-level board spanning the API and web repos.
- **Milestones** are sprints; **tags + Releases** are versions with generated notes.
- **Actions** is CI/CD; branch protection makes the checks mandatory.
- `docs/PROCESS.md` §14 maps every Jira/Confluence/Bitbucket term to its equivalent here.

## Consequences
- The whole process is public with the repo.
- Iteration fields cannot be created by the CLI; one manual step in the UI (documented in TD-4).
