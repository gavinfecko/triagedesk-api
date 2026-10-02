package dev.gavinfecko.triagedesk.identity.api;

import dev.gavinfecko.triagedesk.common.security.CurrentUser;
import dev.gavinfecko.triagedesk.identity.application.UserQueries;
import dev.gavinfecko.triagedesk.identity.application.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserQueries queries;

    public UserController(UserQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/me")
    @Operation(summary = "The calling user")
    public UserView me() {
        return queries.byId(CurrentUser.get().id());
    }
}
