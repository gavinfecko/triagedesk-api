package dev.gavinfecko.triagedesk.sla.api;

import dev.gavinfecko.triagedesk.common.errors.InvalidFieldException;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyService;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyService.PolicyChanges;
import dev.gavinfecko.triagedesk.sla.application.SlaPolicyView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sla/policies")
@Tag(name = "SLA", description = "Policies and business-hours calendars (admin)")
public class SlaPolicyController {

    private final SlaPolicyService policies;

    public SlaPolicyController(SlaPolicyService policies) {
        this.policies = policies;
    }

    public record UpdatePolicyRequest(
            @Nullable @Min(1) Integer firstResponseMinutes,
            @Nullable @Min(2) Integer resolutionMinutes,
            @Nullable UUID calendarId,
            @Nullable Boolean active) {
        boolean isEmpty() {
            return firstResponseMinutes == null && resolutionMinutes == null && calendarId == null && active == null;
        }
    }

    @GetMapping
    @Operation(summary = "The policy for every priority, P1 first")
    public List<SlaPolicyView> list() {
        return policies.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One policy")
    public SlaPolicyView get(@PathVariable UUID id) {
        return policies.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Change a policy's budgets, calendar or active flag (admin)",
            description = "Budgets are minutes on the policy's calendar; resolution_minutes must exceed "
                    + "first_response_minutes. The change applies to tickets created after it; running timers keep "
                    + "the budgets they started with.")
    public SlaPolicyView update(@PathVariable UUID id, @Valid @RequestBody UpdatePolicyRequest request) {
        if (request.isEmpty()) {
            throw new InvalidFieldException(
                    "first_response_minutes",
                    "send at least one of first_response_minutes, resolution_minutes, calendar_id, active");
        }
        return policies.update(
                id,
                new PolicyChanges(
                        request.firstResponseMinutes(),
                        request.resolutionMinutes(),
                        request.calendarId(),
                        request.active()));
    }
}
