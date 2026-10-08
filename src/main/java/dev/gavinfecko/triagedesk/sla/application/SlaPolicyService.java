package dev.gavinfecko.triagedesk.sla.application;

import dev.gavinfecko.triagedesk.common.audit.AuditEvent;
import dev.gavinfecko.triagedesk.common.audit.AuditLog;
import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.sla.domain.CalendarDefinition;
import dev.gavinfecko.triagedesk.sla.domain.SlaPolicy;
import dev.gavinfecko.triagedesk.sla.infra.CalendarRepository;
import dev.gavinfecko.triagedesk.sla.infra.SlaPolicyRepository;
import dev.gavinfecko.triagedesk.ticket.domain.Priority;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA promise per priority. Anyone who works tickets can read the policies; admins edit them. An edit is one
 * audit row and applies to tickets created from then on: timers keep the budgets they were started with.
 */
@Service
public class SlaPolicyService {

    private final SlaPolicyRepository policies;
    private final CalendarRepository calendars;
    private final AuditLog audit;
    private final Clock clock;

    public SlaPolicyService(SlaPolicyRepository policies, CalendarRepository calendars, AuditLog audit, Clock clock) {
        this.policies = policies;
        this.calendars = calendars;
        this.audit = audit;
        this.clock = clock;
    }

    /** A partial edit: a null field keeps its current value. */
    public record PolicyChanges(
            @Nullable Integer firstResponseMinutes,
            @Nullable Integer resolutionMinutes,
            @Nullable UUID calendarId,
            @Nullable Boolean active) {}

    @Transactional(readOnly = true)
    public List<SlaPolicyView> list() {
        return policies.findAll(Sort.by("priority")).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public SlaPolicyView get(UUID id) {
        return view(find(id));
    }

    /** The policy tickets of this priority start with, if one is active. */
    @Transactional(readOnly = true)
    public Optional<SlaPolicyView> activeFor(Priority priority) {
        return policies.findByPriority(priority).filter(SlaPolicy::active).map(this::view);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SlaPolicyView update(UUID id, PolicyChanges changes) {
        SlaPolicy policy = find(id);
        int firstResponse =
                changes.firstResponseMinutes() != null ? changes.firstResponseMinutes() : policy.firstResponseMinutes();
        int resolution = changes.resolutionMinutes() != null ? changes.resolutionMinutes() : policy.resolutionMinutes();
        UUID calendarId = changes.calendarId() != null ? changes.calendarId() : policy.calendarId();
        boolean active = changes.active() != null ? changes.active() : policy.active();
        if (firstResponse < 1) {
            throw new InvalidFieldException("first_response_minutes", "must be at least 1");
        }
        if (resolution <= firstResponse) {
            throw new InvalidFieldException(
                    "resolution_minutes", "must be longer than first_response_minutes (" + firstResponse + ")");
        }
        if (!calendars.existsById(calendarId)) {
            throw new InvalidFieldException("calendar_id", "no such calendar");
        }
        SlaPolicyView before = view(policy);
        policy.update(firstResponse, resolution, calendarId, active, clock.instant());
        SlaPolicyView after = view(policy);
        audit.record(AuditEvent.of("sla.policy_changed")
                .actor(CurrentUser.get().id())
                .change("policy:" + policy.priority(), before, after));
        return after;
    }

    private SlaPolicy find(UUID id) {
        return policies.findById(id).orElseThrow(() -> new NotFoundException("SLA policy", id));
    }

    private SlaPolicyView view(SlaPolicy p) {
        String calendarName =
                calendars.findById(p.calendarId()).map(CalendarDefinition::name).orElse("");
        return new SlaPolicyView(
                p.id(),
                p.priority(),
                p.firstResponseMinutes(),
                p.resolutionMinutes(),
                p.calendarId(),
                calendarName,
                p.active(),
                p.updatedAt());
    }
}
