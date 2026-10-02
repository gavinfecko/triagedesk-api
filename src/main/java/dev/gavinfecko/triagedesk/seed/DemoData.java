package dev.gavinfecko.triagedesk.seed;

import java.util.List;
import java.util.UUID;

/** The demo clinic: who works there and the kinds of problems they report. */
final class DemoData {

    static final String PASSWORD = "Demo-Password-2026";

    record Person(String email, String displayName, String role) {
        UUID id() {
            return SeedIds.of("user:" + email);
        }
    }

    static final Person ADMIN = new Person("admin@clinic.test", "Morgan Lee", "ADMIN");

    static final List<Person> AGENTS = List.of(
            new Person("ana.ruiz@clinic.test", "Ana Ruiz", "AGENT"),
            new Person("ben.okafor@clinic.test", "Ben Okafor", "AGENT"));

    static final List<Person> REQUESTERS = List.of(
            new Person("rosa.diaz@clinic.test", "Rosa Diaz (Front Desk)", "REQUESTER"),
            new Person("kim.tran@clinic.test", "Kim Tran (Nursing)", "REQUESTER"),
            new Person("dr.patel@clinic.test", "Dr. Priya Patel", "REQUESTER"),
            new Person("lee.harper@clinic.test", "Lee Harper (Billing)", "REQUESTER"),
            new Person("sam.cole@clinic.test", "Sam Cole (Reception, Office 2)", "REQUESTER"),
            new Person("jo.martin@clinic.test", "Jo Martin (Practice Manager)", "REQUESTER"));

    // Category ids from R__reference_data.sql.
    static final UUID EHR = UUID.fromString("00000000-0000-4000-8000-000000000201");
    static final UUID PRINTER = UUID.fromString("00000000-0000-4000-8000-000000000202");
    static final UUID NETWORK = UUID.fromString("00000000-0000-4000-8000-000000000203");
    static final UUID EMAIL = UUID.fromString("00000000-0000-4000-8000-000000000204");
    static final UUID HARDWARE = UUID.fromString("00000000-0000-4000-8000-000000000205");
    static final UUID ACCESS = UUID.fromString("00000000-0000-4000-8000-000000000206");
    static final UUID SECURITY = UUID.fromString("00000000-0000-4000-8000-000000000207");
    static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000208");

    record Problem(String title, String description, UUID categoryId, String priority) {}

    static final List<Problem> PROBLEMS = List.of(
            new Problem(
                    "EHR locks me out after one wrong password",
                    "The EHR login shows 'account locked' after a single typo. I have patients waiting.",
                    EHR,
                    "P2_HIGH"),
            new Problem(
                    "Front desk label printer jams on every label",
                    "Patient wristband labels jam halfway out. Restarting the printer did not help.",
                    PRINTER,
                    "P2_HIGH"),
            new Problem(
                    "VPN drops every 10 minutes from home",
                    "Working remotely tonight; the VPN disconnects about every ten minutes and the EHR session is lost.",
                    NETWORK,
                    "P3_MEDIUM"),
            new Problem(
                    "New hire needs a laptop and EHR access by Monday",
                    "Medical assistant starting Monday at Office 2. Needs a laptop, email and an EHR account with MA permissions.",
                    ACCESS,
                    "P3_MEDIUM"),
            new Problem(
                    "Suspicious email asking me to confirm my password",
                    "Got an email that looks like Microsoft asking me to re-enter my password. I did not click anything. Forwarding it to you.",
                    SECURITY,
                    "P2_HIGH"),
            new Problem(
                    "Shared mailbox stopped receiving referrals",
                    "The referrals@ shared mailbox has had no new mail since yesterday afternoon.",
                    EMAIL,
                    "P2_HIGH"),
            new Problem(
                    "Card reader at checkout declines every card",
                    "Every card is declined at the Office 1 checkout terminal; the other terminal works.",
                    HARDWARE,
                    "P2_HIGH"),
            new Problem(
                    "Wi-Fi in exam rooms 3 and 4 is very slow",
                    "Tablets in exam rooms 3 and 4 take a minute to load each chart.",
                    NETWORK,
                    "P3_MEDIUM"),
            new Problem(
                    "Scanner saves documents to the wrong folder",
                    "Scanned insurance cards go to the old shared drive instead of the patient's chart.",
                    HARDWARE,
                    "P4_LOW"),
            new Problem(
                    "EHR is down for the whole office",
                    "Nobody at Office 2 can open the EHR. Error: 'service unavailable'. We are taking paper notes.",
                    EHR,
                    "P1_CRITICAL"),
            new Problem(
                    "Outlook asks for my password every hour",
                    "Outlook keeps prompting for credentials even after I enter them correctly.",
                    EMAIL,
                    "P4_LOW"),
            new Problem(
                    "Need access to the billing reports folder",
                    "I moved to billing last week and cannot open the monthly reports folder.",
                    ACCESS,
                    "P4_LOW"),
            new Problem(
                    "Monitor at reception flickers",
                    "The second monitor at reception flickers on and off every few seconds.",
                    HARDWARE,
                    "P4_LOW"),
            new Problem(
                    "Fax-to-email is not delivering lab results",
                    "Lab results normally arrive as email from the fax line; nothing has come through today.",
                    EMAIL,
                    "P2_HIGH"),
            new Problem(
                    "Lost my phone with the MFA app on it",
                    "My phone was stolen this morning and it has the authenticator app. I cannot log in to anything.",
                    SECURITY,
                    "P1_CRITICAL"),
            new Problem(
                    "E-prescribing fails with a certificate error",
                    "Sending prescriptions from the EHR fails with 'certificate not trusted' since this morning.",
                    EHR,
                    "P2_HIGH"),
            new Problem(
                    "Printer in nurse station prints blank pages",
                    "The nurse station printer feeds paper but prints nothing.",
                    PRINTER,
                    "P3_MEDIUM"),
            new Problem(
                    "Guest Wi-Fi password for the waiting room",
                    "Patients keep asking for Wi-Fi; can we post a guest password that changes monthly?",
                    OTHER,
                    "P4_LOW"),
            new Problem(
                    "Computer very slow after last update",
                    "My workstation takes 10 minutes to start since the update on Tuesday.",
                    HARDWARE,
                    "P3_MEDIUM"),
            new Problem(
                    "Former employee still in the staff email list",
                    "Someone who left in August still gets all-staff email.",
                    ACCESS,
                    "P3_MEDIUM"));

    private DemoData() {}
}
