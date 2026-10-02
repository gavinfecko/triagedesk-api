package dev.gavinfecko.triagedesk.identity.api;

import dev.gavinfecko.triagedesk.identity.application.NotBreached;
import dev.gavinfecko.triagedesk.identity.application.RegistrationService;
import dev.gavinfecko.triagedesk.identity.application.RegistrationService.Registration;
import dev.gavinfecko.triagedesk.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Register, log in, refresh and log out")
public class AuthController {

    private final RegistrationService registration;

    public AuthController(RegistrationService registration) {
        this.registration = registration;
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 2, max = 80) String displayName,

            @NotBlank @Size(min = 12, max = 128) @NotBreached
            String password) {}

    @PostMapping("/register")
    @Operation(summary = "Create a requester account")
    @SecurityRequirements
    public ResponseEntity<UserView> register(@Valid @RequestBody RegisterRequest request) {
        UserView user =
                registration.register(new Registration(request.email(), request.displayName(), request.password()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }
}
