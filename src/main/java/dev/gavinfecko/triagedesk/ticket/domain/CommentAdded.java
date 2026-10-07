package dev.gavinfecko.triagedesk.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/** A comment was written. Notifications (TD-50) tell the other party about PUBLIC ones. */
public record CommentAdded(
        UUID ticketId, String key, UUID commentId, UUID authorId, Visibility visibility, Instant at) {}
