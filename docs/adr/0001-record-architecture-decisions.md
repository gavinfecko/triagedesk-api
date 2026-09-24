# ADR-0001: Record architecture decisions

**Status:** Accepted · **Date:** 2026-09-24

## Context
Decisions made early (framework version, auth model, module layout) shape everything after them. Without a record, six weeks later the reasons are gone and the decision looks arbitrary, especially to a reader who was not there.

## Decision
Keep Architecture Decision Records in `docs/adr/`, one per decision, numbered, in a short Status / Context / Decision / Consequences format. An ADR is written in the same PR as the change it describes. Superseded ADRs are kept and linked from their successor.

## Consequences
- Readers (and interviewers) can see *why*, not just *what*.
- Small overhead per decision; the format is deliberately short so it gets done.
