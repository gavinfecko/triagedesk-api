package dev.gavinfecko.triagedesk.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** A label agents put on tickets. Names are lower-case, 2–30 characters of letters, digits and dashes. */
@Entity
@Table(name = "tags")
public class Tag {

    public static final Pattern NAME = Pattern.compile("^[a-z0-9][a-z0-9-]{0,28}[a-z0-9]$");

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    protected Tag() {}

    public Tag(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    /** Lower-cased and trimmed; null if the result is not a legal tag name. */
    public static String normalize(String raw) {
        String name = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return NAME.matcher(name).matches() ? name : null;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }
}
