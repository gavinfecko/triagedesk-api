package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.time.Instant;
import java.util.UUID;

public record SlaPolicyView(
        UUID id,
        Priority priority,
        int firstResponseMinutes,
        int resolutionMinutes,
        UUID calendarId,
        String calendarName,
        boolean active,
        Instant updatedAt) {}
