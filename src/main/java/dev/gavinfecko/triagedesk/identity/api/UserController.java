package dev.gavinfecko.triagedesk.identity.api;

import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.common.security.Role;
import dev.gavinfecko.triagedesk.common.web.PageResponse;
import dev.gavinfecko.triagedesk.identity.application.NotBreached;
import dev.gavinfecko.triagedesk.identity.application.PasswordService;
import dev.gavinfecko.triagedesk.identity.application.UserAdminService;
import dev.gavinfecko.triagedesk.identity.application.UserAdminService.NewUser;
import dev.gavinfecko.triagedesk.identity.application.UserAdminService.UserChanges;
import dev.gavinfecko.triagedesk.identity.application.UserQueries;
import dev.gavinfecko.triagedesk.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "The caller's own account, and user administration (admins)")
public class UserController {

    private final UserQueries queries;
    private final UserAdminService admin;

    private final PasswordService passwordChanges;

    public UserController(UserQueries queries, UserAdminService admin, PasswordService passwordChanges) {
        this.queries = queries;
        this.admin = admin;
        this.passwordChanges = passwordChanges;
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 2, max = 80) String displayName,
            @NotNull Role role,

            @NotBlank @Size(min = 12, max = 128) @NotBreached
            String temporaryPassword) {}

    public record UpdateUserRequest(
            @Nullable @Size(min = 2, max = 80) String displayName,
            @Nullable Role role) {}

    @GetMapping("/me")
    @Operation(summary = "The calling user")
    public UserView me() {
        return queries.byId(CurrentUser.get().id());
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,

            @NotBlank @Size(min = 12, max = 128) @NotBreached
            String newPassword) {}

    @PostMapping("/me/password")
    @Operation(
            summary = "Change your own password",
            description =
                    "Needs the current password (401 if wrong). Ends every other session of yours and clears "
                            + "the temporary-password requirement; refresh your token afterwards to drop it from the token too.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordChanges.change(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List users, newest first (admin)")
    public PageResponse<UserView> list(
            @RequestParam(required = false) @Nullable Role role,
            @RequestParam(required = false) @Nullable Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return admin.list(role, active, page, size);
    }

    @PostMapping
    @Operation(summary = "Create an account of any role with a temporary password (admin)")
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        UserView user = admin.create(
                new NewUser(request.email(), request.displayName(), request.role(), request.temporaryPassword()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a user or change their role (admin); cannot demote the last active admin")
    public UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return admin.update(id, new UserChanges(request.displayName(), request.role()));
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user (admin): they can no longer log in and their sessions end")
    public UserView deactivate(@PathVariable UUID id) {
        return admin.deactivate(id);
    }
}
