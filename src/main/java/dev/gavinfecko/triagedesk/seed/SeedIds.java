package dev.gavinfecko.triagedesk.seed;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable ids derived from names, so seeding twice touches the same rows. */
final class SeedIds {

    private SeedIds() {}

    static UUID of(String name) {
        return UUID.nameUUIDFromBytes(("triagedesk-demo:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
