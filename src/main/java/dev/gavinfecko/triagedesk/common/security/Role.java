package dev.gavinfecko.triagedesk.common.security;

/** The three roles, most powerful first (ARCHITECTURE.md §6). */
public enum Role {
    ADMIN,
    AGENT,
    REQUESTER;

    /** The Spring Security authority for this role, e.g. {@code ROLE_AGENT}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
