package dev.gavinfecko.triagedesk.sla.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Where a clock stands right now; computed, never stored. */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    BREACHED,
    MET,
    PAUSED,
    CANCELLED;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
