package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.ticket.domain.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    List<Tag> findByNameIn(Collection<String> names);
}
