package dev.gavinfecko.triagedesk.common.errors;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that raise each kind of error the handler must shape. Never on the main classpath. */
@RestController
@RequestMapping("/api/v1/probe")
class ProbeController {

    record CreateThing(
            @NotBlank @Size(min = 3, max = 20) String title,
            @Min(1) int count) {}

    static class ProbeRuleException extends DomainRuleException {
        ProbeRuleException() {
            super("ticket-state-conflict", "Ticket HD-000001 is CLOSED and cannot change");
        }
    }

    private final Validator validator;

    ProbeController(Validator validator) {
        this.validator = validator;
    }

    @PostMapping("/validate")
    CreateThing validate(@Valid @RequestBody CreateThing body) {
        return body;
    }

    /** What a {@code @Validated} service throws when a caller hands it an invalid value. */
    @GetMapping("/service-violation")
    void serviceViolation() {
        throw new ConstraintViolationException(validator.validate(new CreateThing("ab", 0)));
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Ticket", "HD-000001");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ProbeRuleException();
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internal detail that must never reach a client");
    }

    @GetMapping("/ok")
    String ok() {
        return "ok";
    }
}
