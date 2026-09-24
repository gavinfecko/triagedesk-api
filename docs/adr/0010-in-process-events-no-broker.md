# ADR-0010: In-process domain events, no message broker in v1

**Status:** Accepted · **Date:** 2026-09-24

## Context
SLA timers, notifications and audit all react to ticket changes. A broker (RabbitMQ, Kafka) would decouple them across processes but adds infrastructure, and v1 is one process.

## Decision
`TicketService` publishes Spring `ApplicationEvent`s (`ticket.created`, `status_changed`, `first_response`, `priority_changed`, `resolved`, `closed`, `reopened`, `sla.breached`). Listeners in other features subscribe with `@TransactionalEventListener`: audit and SLA in the same transaction (`BEFORE_COMMIT`), notifications after commit. The scheduled jobs use a database lease lock so two instances are safe.

## Consequences
- Zero extra infrastructure; the event names already form the contract a broker would carry.
- A lost after-commit email is logged and counted, not retried; TD-102 (webhooks) would introduce an outbox table if reliable delivery is needed.
