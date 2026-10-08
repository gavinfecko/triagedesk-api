package dev.gavinfecko.triagedesk.sla.domain;

import java.util.List;

/** Everything one breach scan found, published once after the run so admins get one digest, not one email each. */
public record SlaBreachesFound(List<SlaBreached> breaches) {}
