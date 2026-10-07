package dev.gavinfecko.triagedesk.ticket.application;

import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.identity.application.UserDirectory;
import dev.gavinfecko.triagedesk.ticket.application.TicketView.Person;
import dev.gavinfecko.triagedesk.ticket.domain.Ticket;
import dev.gavinfecko.triagedesk.ticket.infra.TicketRepository;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads a ticket's audit trail. Staff see every row. Requesters see the facts they could have
 * observed themselves: the ticket's creation, status, assignee, wording, priority, category and
 * public replies. Internal notes, queue routing and first-response bookkeeping stay internal.
 */
@Service
@Transactional(readOnly = true)
public class TicketAuditService {

    static final Set<String> REQUESTER_VISIBLE = Set.of(
            "ticket.created",
            "ticket.status_changed",
            "ticket.assigned",
            "ticket.edited",
            "ticket.priority_changed",
            "ticket.category_changed",
            "ticket.comment_added");

    private final TicketRepository tickets;
    private final UserDirectory people;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public TicketAuditService(TicketRepository tickets, UserDirectory people, JdbcClient jdbc, JsonMapper json) {
        this.tickets = tickets;
        this.people = people;
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<AuditEntry> trail(String key) {
        CurrentUser actor = CurrentUser.get();
        Ticket ticket = tickets.findByKey(key)
                .filter(t -> actor.isStaff() || t.requesterId().equals(actor.id()))
                .orElseThrow(() -> new NotFoundException("Ticket", key));
        List<Row> rows = jdbc.sql("""
                        select id, action, actor_id, field, before_value::text as before, after_value::text as after, created_at
                        from audit_events where ticket_id = ? order by id""")
                .param(ticket.id())
                .query((rs, i) -> new Row(
                        rs.getLong("id"),
                        rs.getString("action"),
                        rs.getObject("actor_id", UUID.class),
                        rs.getString("field"),
                        rs.getString("before"),
                        rs.getString("after"),
                        rs.getTimestamp("created_at")))
                .list();
        if (!actor.isStaff()) {
            rows = rows.stream()
                    .filter(r -> REQUESTER_VISIBLE.contains(r.action()) && !internalNote(r))
                    .toList();
        }
        Set<UUID> ids = new HashSet<>();
        for (Row r : rows) {
            if (r.actorId() != null) {
                ids.add(r.actorId());
            }
            if ("assignee_id".equals(r.field())) {
                addUuid(ids, r.before());
                addUuid(ids, r.after());
            }
        }
        Map<UUID, String> names = people.displayNames(ids);
        return rows.stream().map(r -> entry(r, names)).toList();
    }

    private record Row(
            long id,
            String action,
            @Nullable UUID actorId,
            @Nullable String field,
            @Nullable String before,
            @Nullable String after,
            Timestamp createdAt) {}

    private static boolean internalNote(Row r) {
        return "ticket.comment_added".equals(r.action()) && "\"INTERNAL\"".equals(r.after());
    }

    private AuditEntry entry(Row r, Map<UUID, String> names) {
        Person actor = r.actorId() == null ? null : new Person(r.actorId(), names.get(r.actorId()));
        boolean assignee = "assignee_id".equals(r.field());
        return new AuditEntry(
                r.id(),
                r.action(),
                actor,
                r.field(),
                value(r.before(), assignee, names),
                value(r.after(), assignee, names),
                r.createdAt().toInstant());
    }

    /** Assignee ids come back as {@code {"id": …, "display_name": …}} so a reader sees a person, not a UUID. */
    private @Nullable JsonNode value(@Nullable String raw, boolean assignee, Map<UUID, String> names) {
        if (raw == null) {
            return null;
        }
        JsonNode node = json.readTree(raw);
        if (assignee && node.isString()) {
            UUID id = UUID.fromString(node.asString());
            return json.valueToTree(new Person(id, names.get(id)));
        }
        return node;
    }

    private static void addUuid(Set<UUID> into, @Nullable String rawJsonString) {
        if (rawJsonString != null && rawJsonString.length() == 38) { // "\"<uuid>\""
            into.add(UUID.fromString(rawJsonString.substring(1, 37)));
        }
    }
}
