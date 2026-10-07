package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.domain.Visibility;
import java.time.Instant;
import java.util.UUID;

public record CommentView(UUID id, Person author, Visibility visibility, String body, Instant createdAt) {}
