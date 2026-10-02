package dev.gavinfecko.triagedesk.identity.application;

import dev.gavinfecko.triagedesk.common.errors.NotFoundException;
import dev.gavinfecko.triagedesk.identity.infra.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserQueries {

    private final UserRepository users;

    public UserQueries(UserRepository users) {
        this.users = users;
    }

    public UserView byId(UUID id) {
        return users.findById(id).map(UserView::of).orElseThrow(() -> new NotFoundException("User", id));
    }
}
