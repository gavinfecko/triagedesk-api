package dev.gavinfecko.triagedesk.support;

import java.util.UUID;

/** Fixed ids from R__reference_data.sql. */
public final class Reference {

    public static final UUID FRONT_DESK = UUID.fromString("00000000-0000-4000-8000-000000000101");
    public static final UUID CLINICAL_SYSTEMS = UUID.fromString("00000000-0000-4000-8000-000000000102");
    public static final UUID NETWORK = UUID.fromString("00000000-0000-4000-8000-000000000103");
    public static final UUID HARDWARE_QUEUE = UUID.fromString("00000000-0000-4000-8000-000000000104");

    public static final UUID EHR = UUID.fromString("00000000-0000-4000-8000-000000000201");
    public static final UUID PRINTER = UUID.fromString("00000000-0000-4000-8000-000000000202");
    public static final UUID SECURITY_INCIDENT = UUID.fromString("00000000-0000-4000-8000-000000000207");

    public static final UUID CLINIC_CALENDAR = UUID.fromString("00000000-0000-4000-8000-000000000301");
    public static final UUID ALWAYS_OPEN_CALENDAR = UUID.fromString("00000000-0000-4000-8000-000000000302");

    public static final UUID POLICY_P1 = UUID.fromString("00000000-0000-4000-8000-000000000401");
    public static final UUID POLICY_P2 = UUID.fromString("00000000-0000-4000-8000-000000000402");
    public static final UUID POLICY_P3 = UUID.fromString("00000000-0000-4000-8000-000000000403");
    public static final UUID POLICY_P4 = UUID.fromString("00000000-0000-4000-8000-000000000404");

    private Reference() {}
}
