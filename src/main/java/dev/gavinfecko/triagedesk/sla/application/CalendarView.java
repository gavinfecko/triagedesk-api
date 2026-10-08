package dev.gavinfecko.triagedesk.sla.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CalendarView(
        UUID id,
        String name,
        String zone,
        boolean alwaysOpen,
        Map<String, List<String>> hours,
        List<Holiday> holidays,
        Instant updatedAt) {

    public record Holiday(LocalDate date, String name) {}
}
