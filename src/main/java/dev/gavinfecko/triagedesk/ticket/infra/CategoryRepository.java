package dev.gavinfecko.triagedesk.ticket.infra;

import dev.gavinfecko.triagedesk.ticket.domain.Category;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {}
